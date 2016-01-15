package eu.kanade.tachiyomi.data.database.tables;

import android.support.annotation.NonNull;

public class MangaCategoryTable {

    @NonNull
    public static final String TABLE = "mangas_categories";

    @NonNull
    public static final String COLUMN_ID = "_id";

    @NonNull
    public static final String COLUMN_MANGA_ID = "manga_id";

    @NonNull
    public static final String COLUMN_CATEGORY_ID = "category_id";

    // This is just class with Meta Data, we don't need instances
    private MangaCategoryTable() {
        throw new IllegalStateException("No instances please");
    }

    // Better than static final field -> allows VM to unload useless String
    // Because you need this string only once per application life on the device
    @NonNull
    public static String getCreateTableQuery() {
        return "CREATE TABLE " + TABLE + "("
                + COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY, "
                + COLUMN_MANGA_ID + " INTEGER NOT NULL, "
                + COLUMN_CATEGORY_ID + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + COLUMN_CATEGORY_ID + ") REFERENCES " + CategoryTable.TABLE + "(" + CategoryTable.COLUMN_ID + ") "
                + "ON DELETE CASCADE, "
                + "FOREIGN KEY(" + COLUMN_MANGA_ID + ") REFERENCES " + MangaTable.TABLE + "(" + MangaTable.COLUMN_ID + ") "
                + "ON DELETE CASCADE"
                + ");";

    }

}
