package eu.kanade.tachiyomi.data.database.tables

object HistoryTable {

    /**
     * Table name
     */
    const val TABLE = "history"

    /**
     * Id column name
     */
    const val COL_ID = "${TABLE}_id"

    /**
     * Chapter id column name
     */
    const val COL_CHAPTER_ID = "${TABLE}_chapter_id"

    /**
     * Last read column name
     */
    const val COL_LAST_READ = "${TABLE}_last_read"

    /**
     * Time read column name
     */
    const val COL_TIME_READ = "${TABLE}_time_read"
}
