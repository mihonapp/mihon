package mihon.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetReadChapterCountByMangaIdAndChapterNumber(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long, chapterNumber: Double): Long {
        return try {
            chapterRepository.getReadChapterCountByMangaIdAndChapterNumber(mangaId, chapterNumber)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            0
        }
    }
}

