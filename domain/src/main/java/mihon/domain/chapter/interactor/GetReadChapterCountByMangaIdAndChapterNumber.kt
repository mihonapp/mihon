package mihon.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetReadChapterCountByMangaIdAndChapterNumber(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long, chapterNumber: Double): Long {
        return try {
            val readChapters = chapterRepository.getReadChapterCountByMangaIdAndChapterNumber(mangaId, chapterNumber)
            logcat(LogPriority.DEBUG, message = { "Read chapters $readChapters - Manga: $mangaId - Chapter: $chapterNumber" }) // TODO Remove this!
            return readChapters
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            0
        }
    }
}

