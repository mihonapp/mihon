package eu.kanade.mangafeed.data.database.tables;

import android.support.annotation.NonNull;

public class MangaSyncTable {

    public static final String TABLE = "manga_sync";

    public static final String COLUMN_ID = "_id";

    public static final String COLUMN_MANGA_ID = "manga_id";

    public static final String COLUMN_SYNC_ID = "sync_id";

    public static final String COLUMN_REMOTE_ID = "remote_id";

    public static final String COLUMN_TITLE = "title";

    public static final String COLUMN_LAST_CHAPTER_READ = "last_chapter_read";

    public static final String COLUMN_STATUS = "status";

    public static final String COLUMN_SCORE = "score";

    @NonNull
    public static String getCreateTableQuery() {
        return "CREATE TABLE " + TABLE + "("
                + COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY, "
                + COLUMN_MANGA_ID + " INTEGER NOT NULL, "
                + COLUMN_SYNC_ID + " INTEGER NOT NULL, "
                + COLUMN_REMOTE_ID + " INTEGER NOT NULL, "
                + COLUMN_TITLE + " TEXT NOT NULL, "
                + COLUMN_LAST_CHAPTER_READ + " INTEGER NOT NULL, "
                + COLUMN_STATUS + " INTEGER NOT NULL, "
                + COLUMN_SCORE + " FLOAT NOT NULL, "
                + "FOREIGN KEY(" + COLUMN_MANGA_ID + ") REFERENCES " + MangaTable.TABLE + "(" + MangaTable.COLUMN_ID + ") "
                + "ON DELETE CASCADE"
                + ");";
    }

}
