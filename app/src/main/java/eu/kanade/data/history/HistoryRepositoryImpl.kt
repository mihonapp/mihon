package eu.kanade.data.history

import androidx.paging.PagingSource
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.chapter.chapterMapper
import eu.kanade.data.manga.mangaMapper
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class HistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : HistoryRepository {

    override fun getHistory(query: String): PagingSource<Long, HistoryWithRelations> {
        return handler.subscribeToPagingSource(
            countQuery = { historyViewQueries.countHistory(query) },
            transacter = { historyViewQueries },
            queryProvider = { limit, offset ->
                historyViewQueries.history(query, limit, offset, historyWithRelationsMapper)
            },
        )
    }

    override suspend fun getNextChapterForManga(mangaId: Long, chapterId: Long): Chapter? {
        val chapter = handler.awaitOne { chaptersQueries.getChapterById(chapterId, chapterMapper) }
        val manga = handler.awaitOne { mangasQueries.getMangaById(mangaId, mangaMapper) }

        if (!chapter.read) {
            return chapter
        }

        val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
            Manga.CHAPTER_SORTING_SOURCE -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
            Manga.CHAPTER_SORTING_NUMBER -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
            Manga.CHAPTER_SORTING_UPLOAD_DATE -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
            else -> throw NotImplementedError("Unknown sorting method")
        }

        val chapters = handler.awaitList { chaptersQueries.getChapterByMangaId(mangaId, chapterMapper) }
            .sortedWith(sortFunction)

        val currChapterIndex = chapters.indexOfFirst { chapter.id == it.id }
        return when (manga.sorting) {
            Manga.CHAPTER_SORTING_SOURCE -> chapters.getOrNull(currChapterIndex + 1)
            Manga.CHAPTER_SORTING_NUMBER -> {
                val chapterNumber = chapter.chapterNumber

                ((currChapterIndex + 1) until chapters.size)
                    .map { chapters[it] }
                    .firstOrNull {
                        it.chapterNumber > chapterNumber &&
                            it.chapterNumber <= chapterNumber + 1
                    }
            }
            Manga.CHAPTER_SORTING_UPLOAD_DATE -> {
                chapters.drop(currChapterIndex + 1)
                    .firstOrNull { it.dateUpload >= chapter.dateUpload }
            }
            else -> throw NotImplementedError("Unknown sorting method")
        }
    }

    override suspend fun resetHistory(historyId: Long) {
        try {
            handler.await { historyQueries.resetHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) {
        try {
            handler.await { historyQueries.resetHistoryByMangaId(mangaId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            handler.await { historyQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }
}
