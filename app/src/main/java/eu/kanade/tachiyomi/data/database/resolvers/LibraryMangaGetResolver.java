package eu.kanade.tachiyomi.data.database.resolvers;

import android.database.Cursor;
import android.support.annotation.NonNull;

import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaStorIOSQLiteGetResolver;
import eu.kanade.tachiyomi.data.database.tables.ChapterTable;
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable;
import eu.kanade.tachiyomi.data.database.tables.MangaTable;

public class LibraryMangaGetResolver extends MangaStorIOSQLiteGetResolver {

    public static final LibraryMangaGetResolver INSTANCE = new LibraryMangaGetResolver();

    public static final String QUERY = String.format(
            "SELECT M.*, COALESCE(MC.%10$s, 0) AS %12$s " +
            "FROM (" +
                "SELECT %1$s.*, COALESCE(C.unread, 0) AS %6$s " +
                "FROM %1$s " +
                "LEFT JOIN (" +
                    "SELECT %5$s, COUNT(*) AS unread " +
                    "FROM %2$s " +
                    "WHERE %7$s = 0 " +
                    "GROUP BY %5$s" +
                ") AS C " +
                "ON %4$s = C.%5$s " +
                "WHERE %8$s = 1 " +
                "GROUP BY %4$s " +
                "ORDER BY %9$s" +
            ") AS M " +
            "LEFT JOIN (SELECT * FROM %3$s) AS MC ON MC.%11$s = M.%4$s",
            MangaTable.TABLE,
            ChapterTable.TABLE,
            MangaCategoryTable.TABLE,
            MangaTable.COLUMN_ID,
            ChapterTable.COLUMN_MANGA_ID,
            MangaTable.COLUMN_UNREAD,
            ChapterTable.COLUMN_READ,
            MangaTable.COLUMN_FAVORITE,
            MangaTable.COLUMN_TITLE,
            MangaCategoryTable.COLUMN_CATEGORY_ID,
            MangaCategoryTable.COLUMN_MANGA_ID,
            MangaTable.COLUMN_CATEGORY
    );

    @Override
    @NonNull
    public Manga mapFromCursor(@NonNull Cursor cursor) {
        Manga manga = super.mapFromCursor(cursor);

        int unreadColumn = cursor.getColumnIndex(MangaTable.COLUMN_UNREAD);
        manga.unread = cursor.getInt(unreadColumn);

        int categoryColumn = cursor.getColumnIndex(MangaTable.COLUMN_CATEGORY);
        manga.category = cursor.getInt(categoryColumn);

        return manga;
    }

}
