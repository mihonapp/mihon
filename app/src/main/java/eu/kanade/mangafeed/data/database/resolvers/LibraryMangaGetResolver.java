package eu.kanade.mangafeed.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.database.tables.MangaTable;

public class LibraryMangaGetResolver extends MangaStorIOSQLiteGetResolver {

    public static final LibraryMangaGetResolver INSTANCE = new LibraryMangaGetResolver();

    @Override
    @NonNull
    public Manga mapFromCursor(@NonNull Cursor cursor) {
        Manga manga = super.mapFromCursor(cursor);

        int unreadColumn = cursor.getColumnIndex(MangaTable.COLUMN_UNREAD);
        manga.unread = cursor.getInt(unreadColumn);

        int categoryColumn = cursor.getColumnIndex(MangaTable.COLUMN_CATEGORY);
        manga.category = cursor.getLong(categoryColumn);

        return manga;
    }

}
