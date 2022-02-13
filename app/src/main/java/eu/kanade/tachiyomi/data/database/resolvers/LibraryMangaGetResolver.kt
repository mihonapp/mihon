package eu.kanade.tachiyomi.data.database.resolvers

import android.database.Cursor
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import eu.kanade.tachiyomi.data.database.mappers.BaseMangaGetResolver
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.tables.MangaTable

class LibraryMangaGetResolver : DefaultGetResolver<LibraryManga>(), BaseMangaGetResolver {

    companion object {
        val INSTANCE = LibraryMangaGetResolver()
    }

    override fun mapFromCursor(cursor: Cursor): LibraryManga {
        val manga = LibraryManga()

        mapBaseFromCursor(manga, cursor)
        manga.unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(MangaTable.COMPUTED_COL_UNREAD_COUNT))
        manga.category = cursor.getInt(cursor.getColumnIndexOrThrow(MangaTable.COL_CATEGORY))
        manga.readCount = cursor.getInt(cursor.getColumnIndexOrThrow(MangaTable.COMPUTED_COL_READ_COUNT))

        return manga
    }
}
