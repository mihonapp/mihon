package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.domain.anilist.service.AnilistWishlistPreferences
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlist
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
@AssistedInject
class BackupRestorer(
    @Assisted private val notifier: BackupNotifier,
    @Assisted private val isSync: Boolean,
    private val context: Context,
    private val downloadCache: DownloadCache,
    private val categoriesRestorer: CategoriesRestorer,
    private val preferenceRestorer: PreferenceRestorer,
    private val extensionStoreRestorer: ExtensionStoreRestorer,
    private val mangaRestorer: MangaRestorer,
    private val backupDecoder: BackupDecoder,
    private val anilistWishlistRepository: AnilistWishlistRepository,
    private val anilistWishlistPreferences: AnilistWishlistPreferences,
    private val mangaRepository: MangaRepository,
    private val getCategories: GetCategories,
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
) {

    @AssistedFactory
    fun interface Factory {
        fun create(notifier: BackupNotifier, isSync: Boolean): BackupRestorer
    }

    private var restoreAmount = 0
    private val restoreProgress = AtomicInt(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()
    var wishlistRestoreReport: WishlistRestoreReport? = null
        private set

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        try {
            restoreFromFile(uri, options)
        } catch (e: CancellationException) {
            wishlistRestoreReport?.complete = false
            throw e
        } catch (e: Throwable) {
            wishlistRestoreReport?.complete = false
            throw e
        }

        // Invalidate download cache to ensure UI reflects any restored downloads
        if (options.libraryEntries) {
            try {
                downloadCache.invalidateCache()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to invalidate download cache after restore" }
            }
        }

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
            wishlistRestoreReport,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val (entryCount, backup) = backupDecoder.decodeMetadata(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreSourceNames(backupMaps)
            restoreAmount += entryCount
        }
        if (options.categories) {
            restoreAmount += 1
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.backupExtensionStores.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }

        val restoredCategoryIds = if (options.categories) {
            restoreCategories(backup.backupCategories)
        } else {
            emptyMap()
        }
        if (backup.backupAnilistWishlist != null) {
            restoreWishlistCategoryPreference(
                backup.backupAnilistWishlist,
                options.appSettings,
                options.categories,
                backup.backupCategories,
                restoredCategoryIds,
            )
        }

        coroutineScope {
            if (options.appSettings) {
                restoreAppPreferences(
                    backup.backupPreferences,
                    backup.backupCategories.takeIf { options.categories },
                )
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            val mangaRestoreJob = if (options.libraryEntries) {
                restoreManga(
                    uri,
                    if (options.categories) backup.backupCategories else emptyList(),
                )
            } else {
                null
            }
            if (options.extensionStores) {
                restoreExtensionStores(backup.backupExtensionStores)
            }

            if (shouldRestoreWishlistMappings(options.libraryEntries, backup.backupAnilistWishlist)) {
                mangaRestoreJob?.join()
                restoreWishlistMappings(backup.backupAnilistWishlist!!)
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    // Without a stub, a source that isn't installed has no name for the next backup to write
    private suspend fun restoreSourceNames(backupSources: List<BackupSource>) {
        backupSources
            .filter { it.name.isNotBlank() }
            .filter {
                sourceManager.get(it.sourceId) == null && stubSourceRepository.getStubSource(it.sourceId) == null
            }
            .forEach { stubSourceRepository.upsertStubSource(it.sourceId, lang = "", name = it.name) }
    }

    private suspend fun restoreCategories(backupCategories: List<BackupCategory>): Map<Long, Long> {
        currentCoroutineContext().ensureActive()
        val restoredCategoryIds = categoriesRestorer(backupCategories)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            progress,
            restoreAmount,
            isSync,
        )
        return restoredCategoryIds
    }

    private fun CoroutineScope.restoreManga(
        uri: Uri,
        backupCategories: List<BackupCategory>,
    ) = launch {
        backupDecoder.decodeEntries(uri)
            .chunked(100)
            .collect { chunk ->
                val restoredAsBatch = try {
                    ensureActive()
                    mangaRestorer.restore(chunk, backupCategories)
                    true
                } catch (e: Exception) {
                    ensureActive()
                    logcat(LogPriority.WARN, e) { "Batch restore failed, retrying entry by entry" }
                    false
                }

                if (restoredAsBatch) {
                    restoreProgress.addAndFetch(chunk.size)
                } else {
                    chunk.forEach {
                        ensureActive()

                        try {
                            mangaRestorer.restore(listOf(it), backupCategories)
                        } catch (e: Exception) {
                            ensureActive()
                            val sourceName = sourceMapping[it.source] ?: it.source.toString()
                            errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                        }

                        restoreProgress.incrementAndFetch()
                    }
                }

                notifier.showRestoreProgress(chunk.last().title, restoreProgress.load(), restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
    ) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private suspend fun restoreWishlistCategoryPreference(
        wishlist: BackupAnilistWishlist?,
        appSettingsEnabled: Boolean,
        categoriesEnabled: Boolean,
        backupCategories: List<BackupCategory>,
        restoredCategoryIds: Map<Long, Long>,
    ) {
        if (wishlist == null) return
        val resolution = resolveWishlistCategory(
            wishlist.category,
            appSettingsEnabled,
            categoriesEnabled,
            backupCategories,
            restoredCategoryIds,
            if (appSettingsEnabled && !categoriesEnabled) getCategories.await() else emptyList(),
        )
        resolution.destinationId?.let { anilistWishlistPreferences.categoryId.set(it) }
        if (!resolution.warning) return
        val report = wishlistRestoreReport ?: WishlistRestoreReport().also { wishlistRestoreReport = it }
        report.categoryWarnings++
    }

    private suspend fun restoreWishlistMappings(wishlist: BackupAnilistWishlist) {
        val report = wishlistRestoreReport ?: WishlistRestoreReport().also { wishlistRestoreReport = it }
        processWishlistMappings(
            wishlist,
            report,
            resolveMangaId = { sourceId, url -> mangaRepository.getMangaByUrlAndSourceId(url, sourceId)?.id },
            insertIfAbsent = anilistWishlistRepository::insertIfAbsent,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupExtensionStores: List<BackupExtensionStore>,
    ) = launch {
        backupExtensionStores
            .chunked(100)
            .forEach { chunk ->
                chunk.forEach {
                    ensureActive()

                    try {
                        extensionStoreRestorer(it)
                    } catch (e: Exception) {
                        errors.add(Date() to "Error Adding Repo: ${it.name} : ${e.message}")
                    }

                    restoreProgress.incrementAndFetch()
                }
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionStores),
                    restoreProgress.load(),
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("mihon_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
