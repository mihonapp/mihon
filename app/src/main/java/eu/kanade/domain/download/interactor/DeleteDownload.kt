package eu.kanade.domain.download.interactor

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.SourceManager
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

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
