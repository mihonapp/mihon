package eu.kanade.tachiyomi.data.massimport

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.download.service.NovelDownloadPreferences
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class MassImportJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get()
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get()
    private val novelDownloadPreferences: NovelDownloadPreferences = Injekt.get()
    private val mangaRepository: MangaRepository = Injekt.get()
    private val setMangaCategories: SetMangaCategories = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
        setSmallIcon(android.R.drawable.stat_sys_download)
        setContentTitle("Mass Import")
        setContentText("Starting...")
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    override suspend fun doWork(): Result {
        val urls = inputData.getStringArray(KEY_URLS)?.toList() ?: return Result.failure()
        val categoryId = inputData.getLong(KEY_CATEGORY_ID, 0L)
        val addToLibrary = inputData.getBoolean(KEY_ADD_TO_LIBRARY, true)
        val fetchChapters = inputData.getBoolean(KEY_FETCH_CHAPTERS, false)

        setForegroundSafely()

        return withIOContext {
            try {
                performImport(urls, categoryId, addToLibrary, fetchChapters)
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                context.cancelNotification(Notifications.ID_MASS_IMPORT_PROGRESS)
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_MASS_IMPORT_PROGRESS,
            notificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun performImport(
        urls: List<String>,
        categoryId: Long,
        addToLibrary: Boolean,
        fetchChapters: Boolean,
    ) {
        val novelSources = getNovelSources()
        if (novelSources.isEmpty()) {
            showCompletionNotification(0, 0, urls.size, "No novel sources installed")
            return
        }

        // Build library index for checking existing novels (normalize trailing slashes)
        val libraryUrlIndex: Set<Pair<Long, String>> = try {
            mangaRepository.getLibraryManga()
                .asSequence()
                .map(LibraryManga::manga)
                .map { it.source to it.url.trimEnd('/') }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }

        // Filter valid URLs
        val validUrls = urls.filter { url ->
            url.startsWith("http://") || url.startsWith("https://")
        }.filter { url ->
            val source = findMatchingSource(url, novelSources) ?: return@filter false
            val path = extractPathFromUrl(url, source.baseUrl)
            !libraryUrlIndex.contains(source.id to path)
        }

        if (validUrls.isEmpty()) {
            showCompletionNotification(0, urls.size - validUrls.size, 0, "All novels already in library")
            return
        }

        val concurrency = novelDownloadPreferences.parallelMassImport().get()
        val completedCount = AtomicInteger(0)
        val addedCount = AtomicInteger(0)
        val skippedCount = AtomicInteger(urls.size - validUrls.size)
        val erroredCount = AtomicInteger(0)
        val activeImports = ConcurrentHashMap<String, Boolean>()

        updateNotification(0, validUrls.size, "Starting import...")

        // Use Flow with flatMapMerge to control concurrency instead of launching all at once
        validUrls.asFlow()
            .flatMapMerge(concurrency) { url ->
                flow {
                    activeImports[url] = true
                    updateNotification(completedCount.get(), validUrls.size, "Processing: ${activeImports.size} active")

                    try {
                        val success = processUrl(url, novelSources, addToLibrary, categoryId, fetchChapters)
                        if (success) {
                            addedCount.incrementAndGet()
                        } else {
                            skippedCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Error importing $url" }
                        erroredCount.incrementAndGet()
                    } finally {
                        activeImports.remove(url)
                        val done = completedCount.incrementAndGet()
                        updateNotification(done, validUrls.size, "Processed $done/${validUrls.size}")
                    }
                    emit(Unit)
                }
            }
            .collect()

        // Update shared state for UI
        _sharedResult.update {
            ImportResult(
                added = addedCount.get(),
                skipped = skippedCount.get(),
                errored = erroredCount.get(),
            )
        }

        showCompletionNotification(addedCount.get(), skippedCount.get(), erroredCount.get(), null)
    }

    private suspend fun processUrl(
        url: String,
        novelSources: List<HttpSource>,
        addToLibrary: Boolean,
        categoryId: Long,
        fetchChapters: Boolean,
    ): Boolean {
        val source = findMatchingSource(url, novelSources) ?: return false
        val path = extractPathFromUrl(url, source.baseUrl)
        if (path.isEmpty()) return false

        // Check if already exists (try both with and without trailing slash for compatibility)
        val existingManga = getMangaByUrlAndSourceId.await(path, source.id)
            ?: getMangaByUrlAndSourceId.await("$path/", source.id)
        if (existingManga != null && existingManga.favorite) {
            return false
        }

        // Fetch novel details
        val sManga = source.getMangaDetails(
            eu.kanade.tachiyomi.source.model.SManga.create().apply {
                this.url = path
            },
        )
        sManga.url = path

        // Convert to local manga
        val manga = networkToLocalManga(sManga.toDomainManga(source.id))

        if (addToLibrary) {
            mangaRepository.update(
                MangaUpdate(
                    id = manga.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )

            if (categoryId > 0L) {
                setMangaCategories.await(manga.id, listOf(categoryId))
            }

            if (fetchChapters) {
                try {
                    val sChapters = source.getChapterList(manga.toSManga())
                    syncChaptersWithSource.await(sChapters, manga.copy(favorite = true), source)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to sync chapters for $url" }
                }
            }
        }

        return true
    }

    private fun updateNotification(current: Int, total: Int, status: String) {
        context.notify(
            Notifications.ID_MASS_IMPORT_PROGRESS,
            notificationBuilder
                .setContentTitle(context.stringResource(MR.strings.mass_import_progress_title))
                .setContentText(status)
                .setProgress(total, current, false)
                .build(),
        )
    }

    private fun showCompletionNotification(added: Int, skipped: Int, errored: Int, message: String?) {
        val text = message ?: "Added: $added, Skipped: $skipped, Errors: $errored"
        context.notify(
            Notifications.ID_MASS_IMPORT_COMPLETE,
            context.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
                setSmallIcon(android.R.drawable.stat_sys_download_done)
                setContentTitle(context.stringResource(MR.strings.mass_import_complete_title))
                setContentText(text)
                setAutoCancel(true)
            }.build(),
        )
    }

    private fun getNovelSources(): List<HttpSource> {
        return sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .filter { it.isNovelSource() }
    }

    private fun findMatchingSource(url: String, sources: List<HttpSource>): HttpSource? {
        val normalizedUrl = stripScheme(url).removeSuffix("/")
        return sources.find { source ->
            try {
                val baseUrl = stripScheme(source.baseUrl).removeSuffix("/")
                normalizedUrl.startsWith(baseUrl)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun stripScheme(url: String): String {
        return url.removePrefix("https://").removePrefix("http://")
    }

    private fun extractPathFromUrl(url: String, baseUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            val urlUri = URI(url)
            val baseHost = baseUri.host?.lowercase()
            val urlHost = urlUri.host?.lowercase()

            val result = if (baseHost != null && urlHost != null && baseHost == urlHost) {
                buildString {
                    append(urlUri.rawPath ?: "")
                    val q = urlUri.rawQuery
                    if (!q.isNullOrBlank()) {
                        append('?')
                        append(q)
                    }
                }
            } else {
                val normalizedBase = stripScheme(baseUrl).removeSuffix("/")
                val normalizedUrl = stripScheme(url)
                if (normalizedUrl.startsWith(normalizedBase)) {
                    normalizedUrl.removePrefix(normalizedBase)
                } else {
                    normalizedUrl
                }
            }
            // Normalize trailing slashes for consistent comparison
            result.trimEnd('/')
        } catch (_: Exception) {
            ""
        }
    }

    data class ImportResult(
        val added: Int = 0,
        val skipped: Int = 0,
        val errored: Int = 0,
    )

    companion object {
        private const val TAG = "MassImportJob"
        const val KEY_URLS = "urls"
        const val KEY_CATEGORY_ID = "categoryId"
        const val KEY_ADD_TO_LIBRARY = "addToLibrary"
        const val KEY_FETCH_CHAPTERS = "fetchChapters"

        // Shared state for UI to observe
        private val _sharedResult = MutableStateFlow<ImportResult?>(null)
        val sharedResult = _sharedResult.asStateFlow()

        private val _sharedProgress = MutableStateFlow<Int?>(null)
        val sharedProgress = _sharedProgress.asStateFlow()

        fun clearResult() {
            _sharedResult.value = null
            _sharedProgress.value = null
        }

        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun start(
            context: Context,
            urls: List<String>,
            categoryId: Long = 0L,
            addToLibrary: Boolean = true,
            fetchChapters: Boolean = false,
        ) {
            clearResult()

            val workRequest = OneTimeWorkRequestBuilder<MassImportJob>()
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        KEY_URLS to urls.toTypedArray(),
                        KEY_CATEGORY_ID to categoryId,
                        KEY_ADD_TO_LIBRARY to addToLibrary,
                        KEY_FETCH_CHAPTERS to fetchChapters,
                    ),
                )
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
