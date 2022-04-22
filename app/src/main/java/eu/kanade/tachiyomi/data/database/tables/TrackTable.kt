package eu.kanade.tachiyomi.data.database.tables

object TrackTable {

    const val TABLE = "manga_sync"

    const val COL_ID = "_id"

    const val COL_MANGA_ID = "manga_id"

    const val COL_SYNC_ID = "sync_id"

    const val COL_MEDIA_ID = "remote_id"

    const val COL_LIBRARY_ID = "library_id"

    const val COL_TITLE = "title"

    const val COL_LAST_CHAPTER_READ = "last_chapter_read"

    const val COL_STATUS = "status"

    const val COL_SCORE = "score"

    const val COL_TOTAL_CHAPTERS = "total_chapters"

    const val COL_TRACKING_URL = "remote_url"

    const val COL_START_DATE = "start_date"

    const val COL_FINISH_DATE = "finish_date"

    val insertFromTempTable: String
        get() =
            """
            |INSERT INTO $TABLE($COL_ID,$COL_MANGA_ID,$COL_SYNC_ID,$COL_MEDIA_ID,$COL_LIBRARY_ID,$COL_TITLE,$COL_LAST_CHAPTER_READ,$COL_TOTAL_CHAPTERS,$COL_STATUS,$COL_SCORE,$COL_TRACKING_URL,$COL_START_DATE,$COL_FINISH_DATE)
            |SELECT $COL_ID,$COL_MANGA_ID,$COL_SYNC_ID,$COL_MEDIA_ID,$COL_LIBRARY_ID,$COL_TITLE,$COL_LAST_CHAPTER_READ,$COL_TOTAL_CHAPTERS,$COL_STATUS,$COL_SCORE,$COL_TRACKING_URL,$COL_START_DATE,$COL_FINISH_DATE
            |FROM ${TABLE}_tmp
            """.trimMargin()
}
