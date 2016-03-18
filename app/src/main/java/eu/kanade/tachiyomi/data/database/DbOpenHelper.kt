package eu.kanade.tachiyomi.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import eu.kanade.tachiyomi.data.database.tables.*

class DbOpenHelper(context: Context) : SQLiteOpenHelper(context, DbOpenHelper.DATABASE_NAME, null, DbOpenHelper.DATABASE_VERSION) {

    companion object {
        /**
         * Name of the database file.
         */
        const val DATABASE_NAME = "tachiyomi.db"

        /**
         * Version of the database.
         */
        const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) = with(db) {
        execSQL(MangaTable.getCreateTableQuery())
        execSQL(ChapterTable.getCreateTableQuery())
        execSQL(MangaSyncTable.getCreateTableQuery())
        execSQL(CategoryTable.getCreateTableQuery())
        execSQL(MangaCategoryTable.getCreateTableQuery())

        // DB indexes
        execSQL(MangaTable.getCreateUrlIndexQuery())
        execSQL(MangaTable.getCreateFavoriteIndexQuery())
        execSQL(ChapterTable.getCreateMangaIdIndexQuery())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

}