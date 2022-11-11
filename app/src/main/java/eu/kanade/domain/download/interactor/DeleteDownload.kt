package eu.kanade.domain.download.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext

class DeleteDownload(
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) = withNonCancellableContext {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(chapters.toList(), manga, source)
        }
    }
}
