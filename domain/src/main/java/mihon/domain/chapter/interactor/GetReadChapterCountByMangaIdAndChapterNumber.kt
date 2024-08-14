package mihon.domain.chapter.interactor

import android.database.sqlite.SQLiteException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetReadChapterCountByMangaIdAndChapterNumber(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long, chapterNumber: Double): Long {
        return try {
            chapterRepository.getReadChapterCountByMangaIdAndChapterNumber(mangaId, chapterNumber)
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, e)
            0
        }
    }
}
