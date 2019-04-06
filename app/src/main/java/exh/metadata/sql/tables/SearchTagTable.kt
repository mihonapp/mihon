package exh.metadata.sql.tables

import eu.kanade.tachiyomi.data.database.tables.MangaTable

object SearchTagTable {
    const val TABLE = "search_tags"

    const val COL_ID = "_id"

    const val COL_MANGA_ID = "manga_id"

    const val COL_NAMESPACE = "namespace"

    const val COL_NAME = "name"

    const val COL_TYPE = "type"

    val createTableQuery: String
        get() = """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_MANGA_ID INTEGER NOT NULL,
            $COL_NAMESPACE TEXT,
            $COL_NAME TEXT NOT NULL,
            $COL_TYPE INT NOT NULL,
            FOREIGN KEY($COL_MANGA_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE CASCADE
            )"""

    val createMangaIdIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_MANGA_ID}_index ON $TABLE($COL_MANGA_ID)"

    val createNamespaceNameIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_NAMESPACE}_${COL_NAME}_index ON $TABLE($COL_NAMESPACE, $COL_NAME)"
}
