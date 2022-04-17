package eu.kanade.data.history.repository

import eu.kanade.data.history.local.HistoryPagingSource
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.tables.HistoryTable
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import rx.Subscription
import rx.schedulers.Schedulers
import java.util.*

class HistoryRepositoryImpl(
    private val db: DatabaseHelper
) : HistoryRepository {

    /**
     * Used to observe changes in the History table
     * as RxJava isn't supported in Paging 3
     */
    private var subscription: Subscription? = null

    /**
     * Paging Source for history table
     */
    override fun getHistory(query: String): HistoryPagingSource {
        subscription?.unsubscribe()
        val pagingSource = HistoryPagingSource(this, query)
        subscription = db.db
            .observeChangesInTable(HistoryTable.TABLE)
            .observeOn(Schedulers.io())
            .subscribe {
                pagingSource.invalidate()
            }
        return pagingSource
    }

    override suspend fun getHistory(limit: Int, page: Int, query: String) = coroutineScope {
        withContext(Dispatchers.IO) {
            // Set date limit for recent manga
            val calendar = Calendar.getInstance().apply {
                time = Date()
                add(Calendar.YEAR, -50)
            }

            db.getRecentManga(calendar.time, limit, page * limit, query)
                .executeAsBlocking()
        }
    }

    override suspend fun getNextChapterForManga(manga: Manga, chapter: Chapter): Chapter? = coroutineScope {
        withContext(Dispatchers.IO) {
            if (!chapter.read) {
                return@withContext chapter
            }

            val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
                Manga.CHAPTER_SORTING_SOURCE -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
                Manga.CHAPTER_SORTING_NUMBER -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
                Manga.CHAPTER_SORTING_UPLOAD_DATE -> { c1, c2 -> c1.date_upload.compareTo(c2.date_upload) }
                else -> throw NotImplementedError("Unknown sorting method")
            }

            val chapters = db.getChapters(manga)
                .executeAsBlocking()
                .sortedWith { c1, c2 -> sortFunction(c1, c2) }

            val currChapterIndex = chapters.indexOfFirst { chapter.id == it.id }
            return@withContext when (manga.sorting) {
                Manga.CHAPTER_SORTING_SOURCE -> chapters.getOrNull(currChapterIndex + 1)
                Manga.CHAPTER_SORTING_NUMBER -> {
                    val chapterNumber = chapter.chapter_number

                    ((currChapterIndex + 1) until chapters.size)
                        .map { chapters[it] }
                        .firstOrNull {
                            it.chapter_number > chapterNumber &&
                                it.chapter_number <= chapterNumber + 1
                        }
                }
                Manga.CHAPTER_SORTING_UPLOAD_DATE -> {
                    chapters.drop(currChapterIndex + 1)
                        .firstOrNull { it.date_upload >= chapter.date_upload }
                }
                else -> throw NotImplementedError("Unknown sorting method")
            }
        }
    }

    override suspend fun resetHistory(history: History): Boolean = coroutineScope {
        withContext(Dispatchers.IO) {
            try {
                history.last_read = 0
                db.upsertHistoryLastRead(history)
                    .executeAsBlocking()
                true
            } catch (e: Throwable) {
                logcat(throwable = e)
                false
            }
        }
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long): Boolean = coroutineScope {
        withContext(Dispatchers.IO) {
            try {
                val history = db.getHistoryByMangaId(mangaId)
                    .executeAsBlocking()
                history.forEach { it.last_read = 0 }
                db.upsertHistoryLastRead(history)
                    .executeAsBlocking()
                true
            } catch (e: Throwable) {
                logcat(throwable = e)
                false
            }
        }
    }

    override suspend fun deleteAllHistory(): Boolean = coroutineScope {
        withContext(Dispatchers.IO) {
            try {
                db.dropHistoryTable()
                    .executeAsBlocking()
                true
            } catch (e: Throwable) {
                logcat(throwable = e)
                false
            }
        }
    }
}
