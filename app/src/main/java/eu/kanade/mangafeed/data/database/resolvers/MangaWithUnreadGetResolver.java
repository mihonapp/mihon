package eu.kanade.mangafeed.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.database.tables.MangaTable;

public class MangaWithUnreadGetResolver extends MangaStorIOSQLiteGetResolver {

    @Override
    public Manga mapFromCursor(@NonNull Cursor cursor) {
        Manga manga = super.mapFromCursor(cursor);
        int unreadColumn = cursor.getColumnIndex(MangaTable.COLUMN_UNREAD);
        if (unreadColumn != -1)
            manga.unread = cursor.getInt(unreadColumn);
        return manga;
    }

}
