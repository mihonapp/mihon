package eu.kanade.mangafeed.data.tables;

import android.support.annotation.NonNull;

/**
 * Created by len on 23/09/2015.
 */
public class MangasTable {

    @NonNull
    public static final String TABLE = "mangas";

    @NonNull
    public static final String COLUMN_ID = "_id";

    @NonNull
    public static final String COLUMN_SOURCE = "source";

    @NonNull
    public static final String COLUMN_URL = "url";

    @NonNull
    public static final String COLUMN_ARTIST = "artist";

    @NonNull
    public static final String COLUMN_AUTHOR = "author" ;

    @NonNull
    public static final String COLUMN_DESCRIPTION = "description";

    @NonNull
    public static final String COLUMN_GENRE = "genre";

    @NonNull
    public static final String COLUMN_TITLE = "title";

    @NonNull
    public static final String COLUMN_STATUS = "status";

    @NonNull
    public static final String COLUMN_THUMBNAIL_URL = "thumbnail_url";

    @NonNull
    public static final String COLUMN_RANK = "rank";

    @NonNull
    public static final String COLUMN_LAST_UPDATE = "last_update";

    @NonNull
    public static final String COLUMN_INITIALIZED = "initialized";

    @NonNull
    public static final String COLUMN_VIEWER = "viewer";

    @NonNull
    public static final String COLUMN_CHAPTER_ORDER = "chapter_order";

    @NonNull
    public static final String COLUMN_UNREAD = "unread";

    // This is just class with Meta Data, we don't need instances
    private MangasTable() {
        throw new IllegalStateException("No instances please");
    }

    // Better than static final field -> allows VM to unload useless String
    // Because you need this string only once per application life on the device
    @NonNull
    public static String getCreateTableQuery() {
        return "CREATE TABLE " + TABLE + "("
                + COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY, "
                + COLUMN_SOURCE + " INTEGER NOT NULL, "
                + COLUMN_URL + " TEXT NOT NULL, "
                + COLUMN_ARTIST + " TEXT NOT NULL, "
                + COLUMN_AUTHOR + " TEXT NOT NULL, "
                + COLUMN_DESCRIPTION + " TEXT NOT NULL, "
                + COLUMN_GENRE + " TEXT NOT NULL, "
                + COLUMN_TITLE + " TEXT NOT NULL, "
                + COLUMN_STATUS + " TEXT NOT NULL, "
                + COLUMN_THUMBNAIL_URL + " TEXT NOT NULL, "
                + COLUMN_RANK + " INTEGER NOT NULL, "
                + COLUMN_LAST_UPDATE + " LONG NOT NULL, "
                + COLUMN_INITIALIZED + " BOOLEAN NOT NULL, "
                + COLUMN_VIEWER + " INTEGER NOT NULL, "
                + COLUMN_CHAPTER_ORDER + " INTEGER NOT NULL"
                + ");";
    }
}
