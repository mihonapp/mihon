package tachiyomi.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * Interactor for removing chapters from the database.
 * This permanently removes chapters, not just their downloads.
 */
class RemoveChapters(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun awaitByIds(chapterIds: List<Long>) {
        try {
            chapterRepository.removeChaptersWithIds(chapterIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun await(chapters: List<Chapter>) {
        awaitByIds(chapters.map { it.id })
    }

    suspend fun awaitByMangaIds(mangaIds: List<Long>) {
        try {
            chapterRepository.removeChaptersByMangaIds(mangaIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
