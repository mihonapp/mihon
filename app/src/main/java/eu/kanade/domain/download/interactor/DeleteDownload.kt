package eu.kanade.domain.download.interactor

import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.repository.TranslatedChapterRepository

class DeleteDownload(
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val translatedChapterRepository: TranslatedChapterRepository,
) {

    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) = withNonCancellableContext {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(chapters.toList(), manga, source)
        }
        chapters.forEach { chapter ->
            translatedChapterRepository.deleteAllForChapter(chapter.id)
        }
    }
}
