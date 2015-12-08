package eu.kanade.mangafeed.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.database.tables.MangaTable;

public class MangaWithUnreadGetResolver extends MangaStorIOSQLiteGetResolver {

    public static final MangaWithUnreadGetResolver instance = new MangaWithUnreadGetResolver();

    @Override
    @NonNull
    public Manga mapFromCursor(@NonNull Cursor cursor) {
        Manga manga = super.mapFromCursor(cursor);
        int unreadColumn = cursor.getColumnIndex(MangaTable.COLUMN_UNREAD);
        manga.unread = cursor.getInt(unreadColumn);
        return manga;
    }

}
