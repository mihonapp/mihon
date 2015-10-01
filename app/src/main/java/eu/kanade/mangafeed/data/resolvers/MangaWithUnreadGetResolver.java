package eu.kanade.mangafeed.data.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.mangafeed.data.tables.MangasTable;


public class MangaWithUnreadGetResolver extends MangaStorIOSQLiteGetResolver {

    @Override
    public Manga mapFromCursor(@NonNull Cursor cursor) {
        Manga manga = super.mapFromCursor(cursor);
        int unreadColumn = cursor.getColumnIndex(MangasTable.COLUMN_UNREAD);
        if (unreadColumn != -1)
            manga.unread = cursor.getInt(unreadColumn);
        return manga;
    }

}
