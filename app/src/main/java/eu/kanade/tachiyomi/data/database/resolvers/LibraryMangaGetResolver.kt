package eu.kanade.tachiyomi.data.database.resolvers

import android.database.Cursor

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaStorIOSQLiteGetResolver
import eu.kanade.tachiyomi.data.database.tables.MangaTable

class LibraryMangaGetResolver : MangaStorIOSQLiteGetResolver() {

    companion object {
        val INSTANCE = LibraryMangaGetResolver()
    }

    override fun mapFromCursor(cursor: Cursor): Manga {
        val manga = super.mapFromCursor(cursor)

        val unreadColumn = cursor.getColumnIndex(MangaTable.COLUMN_UNREAD)
        manga.unread = cursor.getInt(unreadColumn)

        val categoryColumn = cursor.getColumnIndex(MangaTable.COLUMN_CATEGORY)
        manga.category = cursor.getInt(categoryColumn)

        return manga
    }

}
