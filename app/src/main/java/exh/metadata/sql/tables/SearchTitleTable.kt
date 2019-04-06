package exh.metadata.sql.tables

import eu.kanade.tachiyomi.data.database.tables.MangaTable

object SearchTitleTable {
    const val TABLE = "search_titles"

    const val COL_ID = "_id"

    const val COL_MANGA_ID = "manga_id"

    const val COL_TITLE = "title"

    const val COL_TYPE = "type"

    val createTableQuery: String
        get() = """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_MANGA_ID INTEGER NOT NULL,
            $COL_TITLE TEXT NOT NULL,
            $COL_TYPE INT NOT NULL,
            FOREIGN KEY($COL_MANGA_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE CASCADE
            )"""

    val createMangaIdIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_MANGA_ID}_index ON $TABLE($COL_MANGA_ID)"

    val createTitleIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_TITLE}_index ON $TABLE($COL_TITLE)"
}
