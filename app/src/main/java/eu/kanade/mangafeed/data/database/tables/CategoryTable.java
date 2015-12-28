package eu.kanade.mangafeed.data.database.tables;

import android.support.annotation.NonNull;

public class CategoryTable {

    @NonNull
    public static final String TABLE = "categories";

    @NonNull
    public static final String COLUMN_ID = "_id";

    @NonNull
    public static final String COLUMN_NAME = "name";

    @NonNull
    public static final String COLUMN_ORDER = "sort";

    @NonNull
    public static final String COLUMN_FLAGS = "flags";

    // This is just class with Meta Data, we don't need instances
    private CategoryTable() {
        throw new IllegalStateException("No instances please");
    }

    // Better than static final field -> allows VM to unload useless String
    // Because you need this string only once per application life on the device
    @NonNull
    public static String getCreateTableQuery() {
        return "CREATE TABLE " + TABLE + "("
                + COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY, "
                + COLUMN_NAME + " TEXT NOT NULL, "
                + COLUMN_ORDER + " INTEGER NOT NULL, "
                + COLUMN_FLAGS + " INTEGER NOT NULL"
                + ");";

    }
}
