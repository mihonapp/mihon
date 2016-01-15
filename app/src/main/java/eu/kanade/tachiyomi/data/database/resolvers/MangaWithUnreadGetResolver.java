package eu.kanade.tachiyomi.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.tachiyomi.data.database.tables.MangaTable;

public class MangaWithUnreadGetResolver extends MangaStorIOSQLiteGetResolver {

    public static final MangaWithUnreadGetResolver INSTANCE = new MangaWithUnreadGetResolver();

    @Override
    @NonNull
    public Manga mapFromCursor(@NonNull Cursor cursor) {
        Manga manga = super.mapFromCursor(cursor);
        int unreadColumn = cursor.getColumnIndex(MangaTable.COLUMN_UNREAD);
        manga.unread = cursor.getInt(unreadColumn);
        return manga;
    }

}
