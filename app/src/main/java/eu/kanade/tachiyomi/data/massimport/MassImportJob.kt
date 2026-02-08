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
import eu.kanade.tachiyomi.jsplugin.source.JsSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
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
import tachiyomi.domain.library.interactor.RefreshLibraryCache
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.io.File
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
    private val refreshLibraryCache: RefreshLibraryCache = Injekt.get()

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
        setSmallIcon(android.R.drawable.stat_sys_download)
        setContentTitle("Mass Import")
        setContentText("Starting...")
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    override suspend fun doWork(): Result {
        val urls = inputData.getStringArray(KEY_URLS)?.toList()
            ?: inputData.getString(KEY_URLS_FILE)?.let { path ->
                runCatching { File(path).readLines().filter { it.isNotBlank() } }.getOrNull()
            }
            ?: return Result.failure()
        val categoryId = inputData.getLong(KEY_CATEGORY_ID, 0L)
        val addToLibrary = inputData.getBoolean(KEY_ADD_TO_LIBRARY, true)
        val fetchDetails = inputData.getBoolean(KEY_FETCH_DETAILS, true)
        val fetchChapters = inputData.getBoolean(KEY_FETCH_CHAPTERS, false)
        val batchId = inputData.getString(KEY_BATCH_ID) ?: ""

        setForegroundSafely()

        return withIOContext {
            try {
                performImport(urls, categoryId, addToLibrary, fetchDetails, fetchChapters, batchId)
                // Full library cache refresh to update UI after bulk import
                try { refreshLibraryCache.await() } catch (_: Exception) {}
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
                    inputData.getString(KEY_URLS_FILE)?.let { path -> runCatching { File(path).delete() } }
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
        fetchDetails: Boolean,
        fetchChapters: Boolean,
        batchId: String,
    ) {
        updateBatchStatus(batchId, BatchStatus.Running)
        
        val novelSources = getNovelSources()
        if (novelSources.isEmpty()) {
            showCompletionNotification(0, 0, urls.size, "No novel sources installed")
            updateBatchStatus(batchId, BatchStatus.Completed)
            return
        }

        // Build library index - ultra lightweight query that avoids expensive libraryView JOIN
        // Only fetches source + url columns directly from mangas table
        val libraryUrlIndex = try {
            mangaRepository.getFavoriteSourceUrlPairs()
                .asSequence()
                .map { it.first to normalizeUrl(it.second) }
                .toSet()
        } catch (e: Exception) {
            emptySet<Pair<Long, String>>()
        }

        // Filter valid URLs (protocols and not already in library)
        val validUrls = urls.filter { url ->
            url.startsWith("http://") || url.startsWith("https://")
        }.filter { url ->
            val source = findMatchingSource(url, novelSources) ?: return@filter false
            val path = normalizeUrl(extractPathFromUrl(url, getSourceBaseUrl(source)))
            !libraryUrlIndex.contains(source.id to path)
        }

        if (validUrls.isEmpty()) {
            showCompletionNotification(0, urls.size - validUrls.size, 0, "All novels already in library")
            updateBatchStatus(batchId, BatchStatus.Completed)
            return
        }

        // Update batch total to reflect actual work items (validUrls, not all urls)
        _sharedQueue.update { list ->
            list.map { if (it.id == batchId) it.copy(total = validUrls.size) else it }
        }

        val concurrency = novelDownloadPreferences.parallelMassImport().get()
        val completedCount = AtomicInteger(0)
        val addedCount = AtomicInteger(0)
        val skippedCount = AtomicInteger(urls.size - validUrls.size)
        val erroredCount = AtomicInteger(0)
        val activeImports = ConcurrentHashMap<String, Boolean>()
        
        val skippedUrls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val erroredUrls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val errorMessages = ConcurrentHashMap<String, String>() // URL -> error message
        
        // Add initially skipped URLs (duplicates/invalid)
        // We don't have the list of invalid/duplicate URLs here easily as we just filtered them out.
        // But we can infer them or just ignore them for now as they are pre-filtered.
        // The dialog handles pre-filtering feedback.

        updateNotification(0, validUrls.size, "Starting import...")

        val throttlingEnabled = novelDownloadPreferences.enableMassImportThrottling().get()
        // Skip throttling if neither fetch details nor fetch chapters is enabled (dummy entries only)
        val shouldThrottle = throttlingEnabled && (fetchDetails || fetchChapters)
        val globalBaseDelay = novelDownloadPreferences.massImportDelay().get().toLong() // Already in ms
        val globalRandomRange = novelDownloadPreferences.randomDelayRange().get().toLong() // Already in ms

        // Helper function to get delay for a source
        fun getDelayForSource(sourceId: Long): Pair<Long, Long> {
            val override = novelDownloadPreferences.getSourceOverride(sourceId)
            if (override != null && override.enabled) {
                val baseDelay = override.massImportDelay?.toLong() ?: globalBaseDelay
                val randomRange = override.randomDelayRange?.toLong() ?: globalRandomRange
                return Pair(baseDelay, randomRange)
            }
            return Pair(globalBaseDelay, globalRandomRange)
        }

        // Group URLs by source for smarter scheduling
        val urlsWithSource = validUrls.mapNotNull { url ->
            val source = findMatchingSource(url, novelSources) ?: return@mapNotNull null
            url to source
        }

        // Per-source semaphores to serialize requests to the same source
        // This ensures only one request per source at a time when throttling is enabled
        val sourceSemaphores = ConcurrentHashMap<Long, Semaphore>()

        // Use Flow with flatMapMerge to control concurrency
        urlsWithSource.asFlow()
            .flatMapMerge(concurrency) { (url, source) ->
                flow {
                    // Apply per-source throttling before processing
                    if (shouldThrottle) {
                        val sourceId = source.id
                        // Get or create semaphore for this source (permits = 1 for serial access)
                        val sourceSemaphore = sourceSemaphores.getOrPut(sourceId) { Semaphore(1) }
                        
                        // Acquire permit - this ensures only one request per source processes at a time
                        sourceSemaphore.withPermit {
                            // Process the request while holding the permit
                            activeImports[url] = true
                            updateNotification(completedCount.get(), validUrls.size, "Processing: ${activeImports.size} active")

                            try {
                                val success = processUrlWithSource(url, source, addToLibrary, fetchDetails, categoryId, fetchChapters)
                                if (success) {
                                    addedCount.incrementAndGet()
                                } else {
                                    skippedCount.incrementAndGet()
                                    skippedUrls.add(url)
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Error importing $url" }
                                erroredCount.incrementAndGet()
                                erroredUrls.add(url)
                                errorMessages[url] = e.message ?: "Unknown error"
                            } finally {
                                activeImports.remove(url)
                                val done = completedCount.incrementAndGet()
                                updateNotification(done, validUrls.size, "Processed $done/${validUrls.size}")
                                
                                updateBatchProgress(
                                    batchId, 
                                    done, 
                                    validUrls.size, 
                                    addedCount.get(), 
                                    skippedCount.get(), 
                                    erroredCount.get(),
                                    erroredUrls.toList(),
                                    skippedUrls.toList(),
                                    errorMessages.toMap(),
                                )
                            }
                            
                            // Delay AFTER processing (before releasing permit) to throttle next request
                            val (baseDelay, randomRange) = getDelayForSource(sourceId)
                            val delayMs = baseDelay + if (randomRange > 0) Random.nextLong(0, randomRange) else 0L
                            if (delayMs > 0) {
                                logcat(LogPriority.DEBUG) { "Throttling source $sourceId: delaying ${delayMs}ms before next request" }
                                delay(delayMs)
                            }
                        }
                    } else {
                        // No throttling - process normally
                        activeImports[url] = true
                        updateNotification(completedCount.get(), validUrls.size, "Processing: ${activeImports.size} active")

                        try {
                            val success = processUrlWithSource(url, source, addToLibrary, fetchDetails, categoryId, fetchChapters)
                            if (success) {
                                addedCount.incrementAndGet()
                            } else {
                                skippedCount.incrementAndGet()
                                skippedUrls.add(url)
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Error importing $url" }
                            erroredCount.incrementAndGet()
                            erroredUrls.add(url)
                            errorMessages[url] = e.message ?: "Unknown error"
                        } finally {
                            activeImports.remove(url)
                            val done = completedCount.incrementAndGet()
                            updateNotification(done, validUrls.size, "Processed $done/${validUrls.size}")
                            
                            updateBatchProgress(
                                batchId, 
                                done, 
                                validUrls.size, 
                                addedCount.get(), 
                                skippedCount.get(), 
                                erroredCount.get(),
                                erroredUrls.toList(),
                                skippedUrls.toList(),
                                errorMessages.toMap(),
                            )
                        }
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
                skippedUrls = skippedUrls.toList(),
                erroredUrls = erroredUrls.toList(),
            )
        }
        
        updateBatchStatus(batchId, BatchStatus.Completed)

        showCompletionNotification(addedCount.get(), skippedCount.get(), erroredCount.get(), null)
        
        // Note: Library cache is now refreshed per-manga during processUrlWithSource
        // to avoid blocking the database with a massive full refresh query
    }
    
    private fun updateBatchStatus(batchId: String, status: BatchStatus) {
        if (batchId.isEmpty()) return
        _sharedQueue.update { list ->
            list.map { if (it.id == batchId) it.copy(status = status) else it }
        }
    }
    
    private fun updateBatchProgress(
        batchId: String, 
        progress: Int, 
        total: Int, 
        added: Int, 
        skipped: Int, 
        errored: Int,
        erroredUrls: List<String> = emptyList(),
        skippedUrls: List<String> = emptyList(),
        errorMessages: Map<String, String> = emptyMap(),
    ) {
        if (batchId.isEmpty()) return
        _sharedQueue.update { list ->
            list.map { 
                if (it.id == batchId) {
                    it.copy(
                        progress = progress, 
                        // total might be different from initial urls size due to filtering, but let's keep initial total
                        added = added,
                        skipped = skipped,
                        errored = errored,
                        erroredUrls = erroredUrls,
                        skippedUrls = skippedUrls,
                        errorMessages = errorMessages,
                    ) 
                } else it 
            }
        }
    }

    private suspend fun processUrl(
        url: String,
        novelSources: List<CatalogueSource>,
        addToLibrary: Boolean,
        fetchDetails: Boolean,
        categoryId: Long,
        fetchChapters: Boolean,
    ): Boolean {
        val source = findMatchingSource(url, novelSources) ?: return false
        return processUrlWithSource(url, source, addToLibrary, fetchDetails, categoryId, fetchChapters)
    }

    private suspend fun processUrlWithSource(
        url: String,
        source: CatalogueSource,
        addToLibrary: Boolean,
        fetchDetails: Boolean,
        categoryId: Long,
        fetchChapters: Boolean,
    ): Boolean {
        val rawPath = extractPathFromUrl(url, getSourceBaseUrl(source))
        if (rawPath.isEmpty()) return false
        
        // Normalize URL before any operations
        val normalizedPath = normalizeUrl(rawPath)

        // Check if already exists with normalized URL
        val existingManga = getMangaByUrlAndSourceId.await(normalizedPath, source.id)
        if (existingManga != null && existingManga.favorite) {
            return false
        }

        // If neither fetch details nor fetch chapters is selected,
        // create a minimal dummy entry without any network requests
        if (!fetchDetails && !fetchChapters) {
            if (existingManga == null) {
                val placeholderManga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
                    this.url = normalizedPath
                    this.title = normalizedPath.substringAfterLast('/').replace("-", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    this.initialized = false
                }
                val manga = networkToLocalManga(placeholderManga.toDomainManga(source.id))
                // Still add to library if requested
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
                }
            } else if (addToLibrary && !existingManga.favorite) {
                mangaRepository.update(
                    MangaUpdate(
                        id = existingManga.id,
                        favorite = true,
                        dateAdded = System.currentTimeMillis(),
                    ),
                )
                if (categoryId > 0L) {
                    setMangaCategories.await(existingManga.id, listOf(categoryId))
                }
            }
            return true
        }

        // Fetch novel details with normalized URL
        val sManga = source.getMangaDetails(
            eu.kanade.tachiyomi.source.model.SManga.create().apply {
                this.url = normalizedPath
            },
        )
        sManga.url = normalizedPath

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
            
            // Refresh library cache for this specific manga to update UI immediately
            // without blocking the database with a massive full refresh
            try {
                refreshLibraryCache.awaitForManga(manga.id)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to refresh cache for manga ${manga.id}" }
            }
        }

        return true
    }

    private fun updateNotification(current: Int, total: Int, status: String) {
        _sharedProgress.update {
            Progress(current, total, status)
        }
        // Create a new notification builder each time to avoid ConcurrentModificationException
        // when addAction() is called repeatedly on the same builder
        val notification = context.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setContentTitle(context.stringResource(MR.strings.mass_import_progress_title))
            setContentText(status)
            setProgress(total, current, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.stringResource(MR.strings.action_cancel),
                eu.kanade.tachiyomi.data.notification.NotificationReceiver.cancelMassImportPendingBroadcast(context),
            )
        }.build()
        context.notify(Notifications.ID_MASS_IMPORT_PROGRESS, notification)
    }

    private fun showCompletionNotification(added: Int, skipped: Int, errored: Int, message: String?) {
        val text = message ?: "Added: $added, Skipped: $skipped, Errors: $errored"
        
        // Write results to file for persistence (like LibraryUpdateJob does for errors)
        val resultFile = writeResultFile(added, skipped, errored)
        
        val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentTitle(context.stringResource(MR.strings.mass_import_complete_title))
            setContentText(text)
            setAutoCancel(true)
            
            // Add content intent to open the result file if it exists
            if (resultFile.exists()) {
                setContentIntent(
                    eu.kanade.tachiyomi.data.notification.NotificationReceiver.openErrorLogPendingActivity(
                        context,
                        resultFile.getUriCompat(context),
                    )
                )
            }
        }
        
        context.notify(Notifications.ID_MASS_IMPORT_COMPLETE, notificationBuilder.build())
    }

    /**
     * Writes import results to a file for persistence.
     * This allows users to see results even if the app is killed.
     */
    private fun writeResultFile(added: Int, skipped: Int, errored: Int): File {
        try {
            val file = context.createFileInCacheDir("mihon_mass_import_results.txt")
            file.bufferedWriter().use { out ->
                out.write("=== Mass Import Results ===\n")
                out.write("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                out.write("Summary:\n")
                out.write("  Added: $added\n")
                out.write("  Skipped: $skipped\n")
                out.write("  Errors: $errored\n\n")
                
                // Get current batch info from shared queue
                val currentBatch = _sharedQueue.value.lastOrNull { it.status == BatchStatus.Completed || it.status == BatchStatus.Running }
                
                if (currentBatch != null) {
                    if (currentBatch.erroredUrls.isNotEmpty()) {
                        out.write("=== Failed URLs (${currentBatch.erroredUrls.size}) ===\n")
                        currentBatch.erroredUrls.forEach { url ->
                            out.write("$url\n")
                        }
                        out.write("\n")
                    }
                    
                    if (currentBatch.skippedUrls.isNotEmpty()) {
                        out.write("=== Skipped URLs (${currentBatch.skippedUrls.size}) ===\n")
                        currentBatch.skippedUrls.forEach { url ->
                            out.write("$url\n")
                        }
                        out.write("\n")
                    }
                    
                    out.write("=== All Input URLs (${currentBatch.urls.size}) ===\n")
                    currentBatch.urls.forEach { url ->
                        out.write("$url\n")
                    }
                }
            }
            return file
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write mass import result file" }
            return File("")
        }
    }

    private fun getNovelSources(): List<CatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it is HttpSource || it is JsSource }
            .filter { it.isNovelSource() }
    }

    /**
     * Find source that matches the given URL.
     * Prioritizes Kotlin extensions over JS plugins when multiple sources match the same URL.
     */
    private fun findMatchingSource(url: String, sources: List<CatalogueSource>): CatalogueSource? {
        val urlHost = try { URI(url).host?.lowercase()?.removePrefix("www.") } catch (_: Exception) { null }
        val matchingSources = sources.filter { source ->
            try {
                val rawBase = getSourceBaseUrl(source)
                val baseForUri = if (rawBase.startsWith("http")) rawBase else "https://$rawBase"
                val baseUri = URI(baseForUri)
                val baseHost = baseUri.host?.lowercase()?.removePrefix("www.")
                val basePath = baseUri.path?.trimEnd('/')
                if (baseHost.isNullOrEmpty() || urlHost.isNullOrEmpty()) return@filter false

                val hostMatches = urlHost == baseHost || urlHost.endsWith(".$baseHost")
                if (!hostMatches) return@filter false

                if (!basePath.isNullOrBlank() && basePath != "/") {
                    val urlPath = URI(url).path ?: ""
                    urlPath.startsWith(basePath)
                } else {
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        if (matchingSources.isEmpty()) {
            logcat(LogPriority.DEBUG) { "MassImport: No source match for $url host=$urlHost" }
        }
        
        if (matchingSources.isEmpty()) return null
        if (matchingSources.size == 1) return matchingSources.first()
        
        // Prioritize Kotlin extensions over JS plugins
        val kotlinSources = matchingSources.filter { it !is JsSource }
        
        return kotlinSources.firstOrNull() ?: matchingSources.first()
    }

    private fun getSourceBaseUrl(source: CatalogueSource): String {
        return when (source) {
            is HttpSource -> source.baseUrl
            is JsSource -> source.baseUrl
            else -> ""
        }
    }

    private fun stripScheme(url: String): String {
        return url.removePrefix("https://").removePrefix("http://")
    }

    /**
     * Normalize URL by removing trailing slashes, fragment identifiers, and double slashes.
     * This ensures consistent URL comparison across the app.
     */
    private fun normalizeUrl(url: String): String {
        return url.trimEnd('/')
            .substringBefore('#')
            .replace(Regex("(?<!:)//+"), "/") // Remove double slashes except in protocol
    }

    private fun extractPathFromUrl(url: String, baseUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            val urlUri = URI(url)
            val baseHost = baseUri.host?.lowercase()?.removePrefix("www.")
            val urlHost = urlUri.host?.lowercase()?.removePrefix("www.")

            if (baseHost != null && urlHost != null && baseHost == urlHost) {
                buildString {
                    append(urlUri.rawPath ?: "")
                    val q = urlUri.rawQuery
                    if (!q.isNullOrBlank()) {
                        append('?')
                        append(q)
                    }
                }
            } else {
                val normalizedBase = baseUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")
                val normalizedUrl = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                
                if (normalizedUrl.startsWith(normalizedBase)) {
                     var path = normalizedUrl.removePrefix(normalizedBase)
                     if (!path.startsWith("/") && path.isNotEmpty()) {
                         path = "/$path"
                     }
                     path
                } else {
                     // Fallback: return full URL path if hosts mismatch but we assume it's correct source
                     urlUri.rawPath ?: ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    data class ImportResult(
        val added: Int = 0,
        val skipped: Int = 0,
        val errored: Int = 0,
        val skippedUrls: List<String> = emptyList(),
        val erroredUrls: List<String> = emptyList(),
    )

    data class Progress(
        val current: Int,
        val total: Int,
        val status: String,
    )

    data class Batch(
        val id: String,
        val urls: List<String>,
        val categoryId: Long,
        val addToLibrary: Boolean,
        val fetchChapters: Boolean,
        val status: BatchStatus = BatchStatus.Pending,
        val progress: Int = 0,
        val total: Int = 0,
        val added: Int = 0,
        val skipped: Int = 0,
        val errored: Int = 0,
        val erroredUrls: List<String> = emptyList(),
        val skippedUrls: List<String> = emptyList(),
        val errorMessages: Map<String, String> = emptyMap(), // URL -> error message mapping
    )

    enum class BatchStatus {
        Pending,
        Running,
        Completed,
        Cancelled
    }

    companion object {
        private const val TAG = "MassImportJob"
        const val KEY_URLS = "urls"
        const val KEY_URLS_FILE = "urlsFile"
        const val KEY_CATEGORY_ID = "categoryId"
        const val KEY_ADD_TO_LIBRARY = "addToLibrary"
        const val KEY_FETCH_DETAILS = "fetchDetails"
        const val KEY_FETCH_CHAPTERS = "fetchChapters"
        const val KEY_BATCH_ID = "batchId"

        // Shared state for UI to observe
        private val _sharedResult = MutableStateFlow<ImportResult?>(null)
        val sharedResult = _sharedResult.asStateFlow()

        private val _sharedProgress = MutableStateFlow<Progress?>(null)
        val sharedProgress = _sharedProgress.asStateFlow()

        private val _sharedQueue = MutableStateFlow<List<Batch>>(emptyList())
        val sharedQueue = _sharedQueue.asStateFlow()

        fun clearResult() {
            _sharedResult.value = null
            _sharedProgress.value = null
        }

        fun isRunning(context: Context): Boolean {
            val workInfos = context.workManager.getWorkInfosByTag(TAG).get()
            return workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

        fun start(
            context: Context,
            urls: List<String>,
            categoryId: Long = 0L,
            addToLibrary: Boolean = true,
            fetchDetails: Boolean = true,
            fetchChapters: Boolean = false,
        ) {
            val batchId = java.util.UUID.randomUUID().toString()
            val batch = Batch(
                id = batchId,
                urls = urls,
                categoryId = categoryId,
                addToLibrary = addToLibrary,
                fetchChapters = fetchChapters,
                total = urls.size
            )
            
            _sharedQueue.update { it + batch }

            // Offload to file if URL list is large to avoid TransactionTooLargeException
            // Transaction limit is ~1MB, be conservative and offload at 500KB
            val offloadToFile = urls.size > 50 || urls.sumOf { it.length } > 500_000
            val payload = mutableListOf<Pair<String, Any?>>( 
                KEY_CATEGORY_ID to categoryId,
                KEY_ADD_TO_LIBRARY to addToLibrary,
                KEY_FETCH_DETAILS to fetchDetails,
                KEY_FETCH_CHAPTERS to fetchChapters,
                KEY_BATCH_ID to batchId,
            )

            if (offloadToFile) {
                val cacheFile = File(context.cacheDir, "mass_import_$batchId.txt")
                cacheFile.writeText(urls.joinToString("\n"))
                payload += KEY_URLS_FILE to cacheFile.absolutePath
            } else {
                payload += KEY_URLS to urls.toTypedArray()
            }

            val workRequest = OneTimeWorkRequestBuilder<MassImportJob>()
                .addTag(TAG)
                .addTag("batch_$batchId")
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(*payload.toTypedArray()))
                .build()

            // Use unique work name per batch so each can execute independently
            context.workManager.enqueueUniqueWork(
                "${TAG}_$batchId",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }

        fun stop(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
            _sharedQueue.update { list ->
                list.map { if (it.status == BatchStatus.Pending || it.status == BatchStatus.Running) it.copy(status = BatchStatus.Cancelled) else it }
            }
        }
        
        fun cancelBatch(context: Context, batchId: String) {
            // Cancel the actual WorkManager job
            context.workManager.cancelUniqueWork("${TAG}_$batchId")
            _sharedQueue.update { list ->
                list.map { if (it.id == batchId && (it.status == BatchStatus.Pending || it.status == BatchStatus.Running)) it.copy(status = BatchStatus.Cancelled) else it }
            }
        }
        
        fun removeBatch(batchId: String) {
            _sharedQueue.update { list ->
                list.filter { it.id != batchId }
            }
        }
        
        fun clearCompleted() {
            _sharedQueue.update { list ->
                list.filter { it.status != BatchStatus.Completed && it.status != BatchStatus.Cancelled }
            }
        }
        
        /**
         * Reinsert errored URLs from a batch back into the queue as a new batch.
         */
        fun reinsertErrored(context: Context, batch: Batch) {
            if (batch.erroredUrls.isEmpty()) return
            start(
                context = context,
                urls = batch.erroredUrls,
                categoryId = batch.categoryId,
                addToLibrary = batch.addToLibrary,
                fetchChapters = batch.fetchChapters,
            )
        }
        
        /**
         * Requeue a cancelled batch - processes remaining unprocessed URLs.
         * Only works for cancelled batches where progress < total.
         */
        fun requeueCancelled(context: Context, batch: Batch) {
            if (batch.status != BatchStatus.Cancelled) return
            if (batch.progress >= batch.total) return
            
            // Get URLs that weren't processed (from progress onwards)
            val processedCount = batch.progress
            val remainingUrls = if (processedCount < batch.urls.size) {
                batch.urls.drop(processedCount)
            } else {
                // All URLs were attempted, requeue errored ones
                batch.erroredUrls
            }
            
            if (remainingUrls.isEmpty()) return
            
            start(
                context = context,
                urls = remainingUrls,
                categoryId = batch.categoryId,
                addToLibrary = batch.addToLibrary,
                fetchChapters = batch.fetchChapters,
            )
        }
        
        /**
         * Export all URLs from a batch to a string (for file saving).
         */
        fun exportBatchUrls(batch: Batch): String {
            return batch.urls.joinToString("\n")
        }
        
        /**
         * Generate a detailed text report for a batch import.
         * Useful for debugging and sharing import results.
         */
        fun generateReport(batch: Batch): String {
            return buildString {
                appendLine("=== Mass Import Report ===")
                appendLine("Batch ID: ${batch.id}")
                appendLine("Status: ${batch.status}")
                appendLine()
                appendLine("=== Summary ===")
                appendLine("Total URLs: ${batch.urls.size}")
                appendLine("Successfully Added: ${batch.added}")
                appendLine("Skipped (already in library): ${batch.skipped}")
                appendLine("Errors: ${batch.errored}")
                appendLine()
                
                if (batch.erroredUrls.isNotEmpty()) {
                    appendLine("=== Failed URLs (${batch.erroredUrls.size}) ===")
                    batch.erroredUrls.forEach { url ->
                        val errorMsg = batch.errorMessages[url]
                        if (errorMsg != null) {
                            appendLine("$url")
                            appendLine("  Error: $errorMsg")
                        } else {
                            appendLine(url)
                        }
                    }
                    appendLine()
                }
                
                if (batch.skippedUrls.isNotEmpty()) {
                    appendLine("=== Skipped URLs (${batch.skippedUrls.size}) ===")
                    batch.skippedUrls.forEach { url ->
                        appendLine(url)
                    }
                    appendLine()
                }
                
                appendLine("=== All Input URLs (${batch.urls.size}) ===")
                batch.urls.forEach { url ->
                    appendLine(url)
                }
            }
        }
        
        /**
         * Generate errors with messages for clipboard copy.
         */
        fun generateErrorsWithMessages(batch: Batch): String {
            return buildString {
                batch.erroredUrls.forEach { url ->
                    appendLine(url)
                    val errorMsg = batch.errorMessages[url]
                    if (errorMsg != null) {
                        appendLine("  → $errorMsg")
                    }
                }
            }
        }
    }
}
