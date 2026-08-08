package eu.kanade.domain.download.interactor

import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager

class DeleteReadDownloads(
    private val getFavorites: GetFavorites,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    /**
     * Deletes downloaded chapters marked as read across the library.
     * Bookmark and excluded-category prefs are applied by [DownloadManager.deleteChapters].
     *
     * @return number of read downloaded chapters requested for deletion
     */
    suspend fun await(): Int = withNonCancellableContext {
        var deletedCount = 0
        for (manga in getFavorites.await()) {
            val source = sourceManager.get(manga.source) ?: continue
            val chapters = getChaptersByMangaId.await(manga.id)
                .filter { chapter ->
                    chapter.read &&
                        downloadManager.isChapterDownloaded(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                            manga.title,
                            manga.source,
                        )
                }
            if (chapters.isEmpty()) continue

            deletedCount += chapters.size
            downloadManager.deleteChapters(chapters, manga, source)
        }
        deletedCount
    }
}
