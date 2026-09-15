package mihon.desktop.library.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.platform.DesktopNotificationService
import mihon.desktop.platform.NotificationType
import mihon.extension.source.model.SManga
import kotlin.coroutines.coroutineContext

class LibraryUpdateService(
    private val repository: LibraryRepository,
    private val syncService: OnlineMangaSyncService? = null,
    private val downloader: DesktopDownloader? = null,
    private val notificationService: DesktopNotificationService? = null,
    private val refreshManga: (suspend (MangaRecord) -> Unit)? = null,
    private val maxConcurrentSources: Int = 3,
    private val requestTimeoutMs: Long = 60_000L,
) {

    init {
        require(maxConcurrentSources > 0)
        require(requestTimeoutMs > 0)
        require(syncService != null || refreshManga != null)
    }

    suspend fun updateLibrary(
        options: LibraryUpdateOptions = LibraryUpdateOptions(),
        onProgress: ((LibraryUpdateProgress) -> Unit)? = null,
        throttleDelayMs: Long = 200L,
    ): LibraryUpdateReport = withContext(Dispatchers.IO) {
        val allFavorites = repository.allMangaSnapshot().filter {
            it.favorite && it.sourceId != 0L && (options.mangaIds == null || it.id in options.mangaIds)
        }
        val categoryLinks = repository.mangaCategoryLinksSnapshot()
        val importedPreferences = repository.allPreferenceSnapshots().associateBy { it.key }
        val includedCategoryIds = options.includedCategoryIds
            ?: importedPreferences["library_update_categories"]?.valueJson.parseCategoryIds()
        val excludedCategoryIds = options.excludedCategoryIds
            ?: importedPreferences["library_update_categories_exclude"]?.valueJson.parseCategoryIds()

        val candidates = allFavorites.filter { manga ->
            val chapters = repository.chapterSnapshot(manga.id)
            // 1. Check completed filter
            if (options.skipCompleted && isMangaCompleted(manga)) {
                return@filter false
            }

            // 2. Check unread filter
            if (options.skipUnread) {
                val hasUnread = chapters.any { !it.read }
                if (hasUnread) {
                    return@filter false
                }
            }

            if (options.skipNotStarted && chapters.isNotEmpty()) {
                val hasStarted = chapters.any { it.read || it.lastPageRead > 0L }
                if (!hasStarted) return@filter false
            }

            // 3. Check categories filter
            val mangaCategories = categoryLinks[manga.id].orEmpty()
            if (!includedCategoryIds.isNullOrEmpty() && mangaCategories.none { it in includedCategoryIds }) {
                return@filter false
            }
            if (!excludedCategoryIds.isNullOrEmpty() && mangaCategories.any { it in excludedCategoryIds }) {
                return@filter false
            }

            true
        }

        val progressMutex = Mutex()
        val sourceSlots = Semaphore(maxConcurrentSources)
        var started = 0
        val resultsBySource = coroutineScope {
            candidates.groupBy { it.sourceId }.values.map { sourceManga ->
                async {
                    sourceSlots.withPermit {
                        sourceManga.mapIndexed { index, manga ->
                            coroutineContext.ensureActive()
                            progressMutex.withLock {
                                started++
                                onProgress?.invoke(
                                    LibraryUpdateProgress(
                                        currentMangaTitle = manga.title,
                                        currentIndex = started,
                                        totalManga = candidates.size,
                                        currentSourceId = manga.sourceId,
                                    ),
                                )
                            }
                            val result = updateManga(manga, options)
                            if (throttleDelayMs > 0 && index < sourceManga.size - 1) delay(throttleDelayMs)
                            result
                        }
                    }
                }
            }.awaitAll()
        }
        val candidateOrder = candidates.mapIndexed { index, manga -> manga.id to index }.toMap()
        val results = resultsBySource.flatten().sortedBy { candidateOrder[it.mangaId] }
        val newChaptersTotal = results.sumOf { it.newChapters.size }
        val report = LibraryUpdateReport(
            totalMangaChecked = candidates.size,
            updatedMangaCount = results.count { it.newChapters.isNotEmpty() },
            newChaptersTotal = newChaptersTotal,
            results = results,
            errors = results.mapNotNull { it.error },
        )

        // Notify user via Desktop Notification Service
        if (newChaptersTotal > 0) {
            val mangaCount = results.count { it.newChapters.isNotEmpty() }
            val firstManga = results.firstOrNull { it.newChapters.isNotEmpty() }?.title ?: "Manga"
            val message = if (mangaCount == 1) {
                "Found $newChaptersTotal new chapter(s) for '$firstManga'"
            } else {
                "Found $newChaptersTotal new chapter(s) across $mangaCount manga including '$firstManga'"
            }
            notificationService?.notify(
                title = "Library Updated",
                message = message,
                type = NotificationType.INFO,
            )
        }

        report
    }

    private suspend fun updateManga(manga: MangaRecord, options: LibraryUpdateOptions): MangaUpdateItemResult {
        try {
            val existingChapterUrls = repository.chapterSnapshot(manga.id).map { it.url }.toSet()
            val refreshed = withTimeoutOrNull(requestTimeoutMs) {
                if (refreshManga != null) {
                    refreshManga.invoke(manga)
                } else {
                    syncService!!.prepareOnlineMangaForReading(
                        sourceId = manga.sourceId,
                        manga = SManga(
                            url = manga.url,
                            title = manga.title,
                            artist = manga.artist,
                            author = manga.author,
                            description = manga.description,
                            genre = emptyList(),
                            status = manga.status.toInt(),
                            thumbnailUrl = manga.thumbnailUrl,
                            initialized = manga.initialized,
                        ),
                        forceRefresh = true,
                    )
                }
                true
            } ?: false
            check(refreshed) { "Source ${manga.sourceId} timed out after $requestTimeoutMs ms" }
            val newlyAdded = repository.chapterSnapshot(manga.id).filter { it.url !in existingChapterUrls }
            if (newlyAdded.isNotEmpty() && options.autoDownloadNewChapters && downloader != null) {
                downloader.enqueue(
                    sourceId = manga.sourceId,
                    mangaId = manga.id,
                    mangaTitle = manga.title,
                    chapters = newlyAdded,
                    autoStart = true,
                )
            }
            return MangaUpdateItemResult(manga.id, manga.title, newlyAdded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return MangaUpdateItemResult(
                manga.id,
                manga.title,
                emptyList(),
                "Failed to update '${manga.title}': ${error.message ?: "Unknown error"}",
            )
        }
    }

    private fun isMangaCompleted(manga: MangaRecord): Boolean {
        // 2 = COMPLETED, 4 = PUBLISHING_FINISHED
        return manga.status == 2L || manga.status == 4L
    }

    private fun String?.parseCategoryIds(): Set<Long>? {
        if (this == null) return null
        return Regex("""\d+""").findAll(this)
            .mapNotNull { match -> match.value.toLongOrNull() }
            .filter { it > 0L }
            .toSet()
    }
}
