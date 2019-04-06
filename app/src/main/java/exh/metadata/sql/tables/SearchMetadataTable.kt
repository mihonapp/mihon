package exh.metadata.sql.tables

import eu.kanade.tachiyomi.data.database.tables.MangaTable

object SearchMetadataTable {
    const val TABLE = "search_metadata"

    const val COL_MANGA_ID = "manga_id"

    const val COL_UPLOADER = "uploader"

    const val COL_EXTRA = "extra"

    const val COL_INDEXED_EXTRA = "indexed_extra"

    const val COL_EXTRA_VERSION = "extra_version"

    // Insane foreign, primary key to avoid touch manga table
    val createTableQuery: String
        get() = """CREATE TABLE $TABLE(
            $COL_MANGA_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_UPLOADER TEXT,
            $COL_EXTRA TEXT NOT NULL,
            $COL_INDEXED_EXTRA TEXT,
            $COL_EXTRA_VERSION INT NOT NULL,
            FOREIGN KEY($COL_MANGA_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE CASCADE
            )"""

    val createUploaderIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_UPLOADER}_index ON $TABLE($COL_UPLOADER)"

    val createIndexedExtraIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_INDEXED_EXTRA}_index ON $TABLE($COL_INDEXED_EXTRA)"
}
