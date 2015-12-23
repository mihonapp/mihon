package eu.kanade.mangafeed.data.database.tables;

import android.support.annotation.NonNull;

public class MangaTable {

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
    public static final String COLUMN_FAVORITE = "favorite";

    @NonNull
    public static final String COLUMN_LAST_UPDATE = "last_update";

    @NonNull
    public static final String COLUMN_INITIALIZED = "initialized";

    @NonNull
    public static final String COLUMN_VIEWER = "viewer";

    @NonNull
    public static final String COLUMN_CHAPTER_FLAGS = "chapter_flags";

    @NonNull
    public static final String COLUMN_UNREAD = "unread";

    @NonNull
    public static final String COLUMN_CATEGORY = "category";

    // This is just class with Meta Data, we don't need instances
    private MangaTable() {
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
                + COLUMN_ARTIST + " TEXT, "
                + COLUMN_AUTHOR + " TEXT, "
                + COLUMN_DESCRIPTION + " TEXT, "
                + COLUMN_GENRE + " TEXT, "
                + COLUMN_TITLE + " TEXT NOT NULL, "
                + COLUMN_STATUS + " INTEGER NOT NULL, "
                + COLUMN_THUMBNAIL_URL + " TEXT, "
                + COLUMN_FAVORITE + " INTEGER NOT NULL, "
                + COLUMN_LAST_UPDATE + " LONG, "
                + COLUMN_INITIALIZED + " BOOLEAN NOT NULL, "
                + COLUMN_VIEWER + " INTEGER NOT NULL, "
                + COLUMN_CHAPTER_FLAGS + " INTEGER NOT NULL"
                + ");";

    }

    public static String getCreateUrlIndexQuery() {
        return "CREATE INDEX " + TABLE + "_" + COLUMN_URL + "_index ON " + TABLE + "(" + COLUMN_URL + ");";
    }

    public static String getCreateFavoriteIndexQuery() {
        return "CREATE INDEX " + TABLE + "_" + COLUMN_FAVORITE + "_index ON " + TABLE + "(" + COLUMN_FAVORITE + ");";

    }
}
