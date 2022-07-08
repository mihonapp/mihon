package eu.kanade.domain.download.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class DeleteDownload(
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    suspend fun awaitAll(manga: Manga, vararg values: Chapter) = withContext(NonCancellable) {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(values.map { it.toDbChapter() }, manga, source)
        }
    }
}
