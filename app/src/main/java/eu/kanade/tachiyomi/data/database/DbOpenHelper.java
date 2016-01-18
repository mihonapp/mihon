package eu.kanade.tachiyomi.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import eu.kanade.tachiyomi.data.database.tables.CategoryTable;
import eu.kanade.tachiyomi.data.database.tables.ChapterTable;
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable;
import eu.kanade.tachiyomi.data.database.tables.MangaSyncTable;
import eu.kanade.tachiyomi.data.database.tables.MangaTable;

public class DbOpenHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "tachiyomi.db";
    public static final int DATABASE_VERSION = 1;

    public DbOpenHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(MangaTable.getCreateTableQuery());
        db.execSQL(ChapterTable.getCreateTableQuery());
        db.execSQL(MangaSyncTable.getCreateTableQuery());
        db.execSQL(CategoryTable.getCreateTableQuery());
        db.execSQL(MangaCategoryTable.getCreateTableQuery());

        // DB indexes
        db.execSQL(MangaTable.getCreateUrlIndexQuery());
        db.execSQL(MangaTable.getCreateFavoriteIndexQuery());
        db.execSQL(ChapterTable.getCreateMangaIdIndexQuery());
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

}