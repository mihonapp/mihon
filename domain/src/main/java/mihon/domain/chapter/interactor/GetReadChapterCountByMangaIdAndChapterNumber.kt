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
            val readChapters = chapterRepository.getReadChapterCountByMangaIdAndChapterNumber(mangaId, chapterNumber)

            // TODO Remove this!
            logcat(
                LogPriority.DEBUG,
                message = { "Read chapters $readChapters - Manga: $mangaId - Chapter: $chapterNumber" }
            )
            return readChapters
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, e)
            0
        }
    }
}
