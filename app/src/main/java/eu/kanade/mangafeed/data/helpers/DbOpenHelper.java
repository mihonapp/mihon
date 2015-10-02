package eu.kanade.mangafeed.data.helpers;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import eu.kanade.mangafeed.data.tables.ChaptersTable;
import eu.kanade.mangafeed.data.tables.MangasTable;

public class DbOpenHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "mangafeed.db";
    public static final int DATABASE_VERSION = 1;

    public DbOpenHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(MangasTable.getCreateTableQuery());
        db.execSQL(ChaptersTable.getCreateTableQuery());
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // no impl
    }

    @Override
    public void onConfigure(SQLiteDatabase db){
        db.setForeignKeyConstraintsEnabled(true);
    }

}