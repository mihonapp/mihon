package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.database.resolvers.HistoryUpsertResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaChapterHistoryGetResolver
import eu.kanade.tachiyomi.data.database.tables.HistoryTable
import java.util.Date

interface HistoryQueries : DbProvider {

    /**
     * Insert history into database
     * @param history object containing history information
     */
    fun insertHistory(history: History) = db.put().`object`(history).prepare()

    /**
     * Returns history of recent manga containing last read chapter
     * @param date recent date range
     * @param limit the limit of manga to grab
     * @param offset offset the db by
     * @param search what to search in the db history
     */
    fun getRecentManga(date: Date, limit: Int = 25, offset: Int = 0, search: String = "") = db.get()
        .listOfObjects(MangaChapterHistory::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getRecentMangasQuery(search))
                .args(date.time, limit, offset)
                .observesTables(HistoryTable.TABLE)
                .build(),
        )
        .withGetResolver(MangaChapterHistoryGetResolver.INSTANCE)
        .prepare()

    fun getHistoryByMangaId(mangaId: Long) = db.get()
        .listOfObjects(History::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getHistoryByMangaId())
                .args(mangaId)
                .observesTables(HistoryTable.TABLE)
                .build(),
        )
        .prepare()

    fun getHistoryByChapterUrl(chapterUrl: String) = db.get()
        .`object`(History::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getHistoryByChapterUrl())
                .args(chapterUrl)
                .observesTables(HistoryTable.TABLE)
                .build(),
        )
        .prepare()

    /**
     * Updates the history last read.
     * Inserts history object if not yet in database
     * @param history history object
     */
    fun upsertHistoryLastRead(history: History) = db.put()
        .`object`(history)
        .withPutResolver(HistoryUpsertResolver())
        .prepare()

    /**
     * Updates the history last read.
     * Inserts history object if not yet in database
     * @param historyList history object list
     */
    fun upsertHistoryLastRead(historyList: List<History>) = db.put()
        .objects(historyList)
        .withPutResolver(HistoryUpsertResolver())
        .prepare()

    fun resetHistoryLastRead(historyId: Long) = db.executeSQL()
        .withQuery(
            RawQuery.builder()
                .query(
                    """
                UPDATE ${HistoryTable.TABLE} 
                SET history_last_read = 0
                WHERE ${HistoryTable.COL_ID} = $historyId  
                    """.trimIndent()
                )
                .build()
        )
        .prepare()

    fun resetHistoryLastRead(historyIds: List<Long>) = db.executeSQL()
        .withQuery(
            RawQuery.builder()
                .query(
                    """
                UPDATE ${HistoryTable.TABLE} 
                SET history_last_read = 0
                WHERE ${HistoryTable.COL_ID} in ${historyIds.joinToString(",", "(", ")")}  
                    """.trimIndent()
                )
                .build()
        )
        .prepare()

    fun dropHistoryTable() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(HistoryTable.TABLE)
                .build(),
        )
        .prepare()

    fun deleteHistoryNoLastRead() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(HistoryTable.TABLE)
                .where("${HistoryTable.COL_LAST_READ} = ?")
                .whereArgs(0)
                .build(),
        )
        .prepare()
}
