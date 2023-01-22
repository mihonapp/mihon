package eu.kanade.domain.chapter.interactor

import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetChapterByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): List<Chapter> {
        return try {
            chapterRepository.getChapterByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
