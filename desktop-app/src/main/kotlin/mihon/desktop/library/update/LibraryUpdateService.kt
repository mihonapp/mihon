package mihon.desktop.library.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.platform.DesktopNotificationService
import mihon.desktop.platform.NotificationType
import mihon.extension.source.model.SManga

class LibraryUpdateService(
    private val repository: LibraryRepository,
    private val syncService: OnlineMangaSyncService,
    private val downloader: DesktopDownloader? = null,
    private val notificationService: DesktopNotificationService? = null,
) {

    suspend fun updateLibrary(
        options: LibraryUpdateOptions = LibraryUpdateOptions(),
        onProgress: ((LibraryUpdateProgress) -> Unit)? = null,
        throttleDelayMs: Long = 200L,
    ): LibraryUpdateReport = withContext(Dispatchers.IO) {
        val allFavorites = repository.allMangaSnapshot().filter { it.favorite }
        val categoryLinks = repository.mangaCategoryLinksSnapshot()

        val candidates = allFavorites.filter { manga ->
            // 1. Check completed filter
            if (options.skipCompleted && isMangaCompleted(manga)) {
                return@filter false
            }

            // 2. Check unread filter
            if (options.skipUnread) {
                val chapters = repository.chapterSnapshot(manga.id)
                val hasUnread = chapters.any { !it.read }
                if (hasUnread) {
                    return@filter false
                }
            }

            // 3. Check categories filter
            if (options.categoryIds != null && options.categoryIds.isNotEmpty()) {
                val mangaCats = categoryLinks[manga.id] ?: emptyList()
                if (mangaCats.none { it in options.categoryIds }) {
                    return@filter false
                }
            }

            true
        }

        val results = mutableListOf<MangaUpdateItemResult>()
        val errors = mutableListOf<String>()
        var newChaptersTotal = 0
        var updatedMangaCount = 0

        candidates.forEachIndexed { index, manga ->
            onProgress?.invoke(
                LibraryUpdateProgress(
                    currentMangaTitle = manga.title,
                    currentIndex = index + 1,
                    totalManga = candidates.size,
                ),
            )

            val existingChapterUrls = repository.chapterSnapshot(manga.id).map { it.url }.toSet()

            try {
                val sManga = SManga(
                    url = manga.url,
                    title = manga.title,
                    artist = manga.artist,
                    author = manga.author,
                    description = manga.description,
                    genre = emptyList(),
                    status = manga.status.toInt(),
                    thumbnailUrl = manga.thumbnailUrl,
                    initialized = manga.initialized,
                )

                syncService.addOrUpdateOnlineManga(
                    sourceId = manga.sourceId,
                    manga = sManga,
                    forceRefresh = true,
                )

                val latestChapters = repository.chapterSnapshot(manga.id)
                val newlyAdded = latestChapters.filter { it.url !in existingChapterUrls }

                if (newlyAdded.isNotEmpty()) {
                    newChaptersTotal += newlyAdded.size
                    updatedMangaCount++

                    if (options.autoDownloadNewChapters && downloader != null) {
                        downloader.enqueue(
                            sourceId = manga.sourceId,
                            mangaId = manga.id,
                            mangaTitle = manga.title,
                            chapters = newlyAdded,
                            autoStart = true,
                        )
                    }
                }

                results.add(
                    MangaUpdateItemResult(
                        mangaId = manga.id,
                        title = manga.title,
                        newChapters = newlyAdded,
                    ),
                )
            } catch (e: Exception) {
                val err = "Failed to update '${manga.title}': ${e.message ?: "Unknown error"}"
                errors.add(err)
                results.add(
                    MangaUpdateItemResult(
                        mangaId = manga.id,
                        title = manga.title,
                        newChapters = emptyList(),
                        error = err,
                    ),
                )
            }

            if (throttleDelayMs > 0 && index < candidates.size - 1) {
                delay(throttleDelayMs)
            }
        }

        val report = LibraryUpdateReport(
            totalMangaChecked = candidates.size,
            updatedMangaCount = updatedMangaCount,
            newChaptersTotal = newChaptersTotal,
            results = results,
            errors = errors,
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

    private fun isMangaCompleted(manga: MangaRecord): Boolean {
        // 2 = COMPLETED, 4 = PUBLISHING_FINISHED
        return manga.status == 2L || manga.status == 4L
    }
}
