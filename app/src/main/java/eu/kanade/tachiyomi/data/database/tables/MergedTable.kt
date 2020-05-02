package eu.kanade.tachiyomi.data.database.tables

object MergedTable {

    const val TABLE = "merged"

    const val COL_MERGE_ID = "mergeID"

    const val COL_MANGA_ID = "mangaID"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_MERGE_ID INTEGER NOT NULL,
            $COL_MANGA_ID INTEGER NOT NULL
            )"""

    val createIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_MERGE_ID}_index ON $TABLE($COL_MERGE_ID)"
}
