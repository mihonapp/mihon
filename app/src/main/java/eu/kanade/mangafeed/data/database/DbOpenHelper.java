package eu.kanade.mangafeed.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import eu.kanade.mangafeed.data.database.tables.MangaSyncTable;
import eu.kanade.mangafeed.data.database.tables.ChapterTable;
import eu.kanade.mangafeed.data.database.tables.MangaTable;

public class DbOpenHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "mangafeed.db";
    public static final int DATABASE_VERSION = 3;

    public DbOpenHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(MangaTable.getCreateTableQuery());
        db.execSQL(ChapterTable.getCreateTableQuery());
        db.execSQL(MangaSyncTable.getCreateTableQuery());
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3)
            db.execSQL(MangaSyncTable.getCreateTableQuery());
    }

    @Override
    public void onConfigure(SQLiteDatabase db){
        db.setForeignKeyConstraintsEnabled(true);
    }

}