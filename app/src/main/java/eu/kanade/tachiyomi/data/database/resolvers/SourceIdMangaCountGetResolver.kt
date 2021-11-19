package eu.kanade.tachiyomi.data.database.resolvers

import android.annotation.SuppressLint
import android.database.Cursor
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import eu.kanade.tachiyomi.data.database.models.SourceIdMangaCount
import eu.kanade.tachiyomi.data.database.tables.MangaTable

class SourceIdMangaCountGetResolver : DefaultGetResolver<SourceIdMangaCount>() {

    companion object {
        val INSTANCE = SourceIdMangaCountGetResolver()
        const val COL_COUNT = "manga_count"
    }

    @SuppressLint("Range")
    override fun mapFromCursor(cursor: Cursor): SourceIdMangaCount {
        val sourceID = cursor.getLong(cursor.getColumnIndex(MangaTable.COL_SOURCE))
        val count = cursor.getInt(cursor.getColumnIndex(COL_COUNT))

        return SourceIdMangaCount(sourceID, count)
    }
}
