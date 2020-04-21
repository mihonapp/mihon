package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import eu.kanade.tachiyomi.data.database.tables.CategoryTable
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.HistoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.database.tables.MergedTable
import eu.kanade.tachiyomi.data.database.tables.SearchMetadataTable
import eu.kanade.tachiyomi.data.database.tables.TrackTable
import exh.metadata.sql.tables.SearchMetadataTable
import exh.metadata.sql.tables.SearchTagTable
import exh.metadata.sql.tables.SearchTitleTable

class DbOpenCallback : SupportSQLiteOpenHelper.Callback(DATABASE_VERSION) {

    companion object {
        /**
         * Name of the database file.
         */
        const val DATABASE_NAME = "tachiyomi.db"

        /**
         * Version of the database.
         */
        const val DATABASE_VERSION = 0 // [SY]
    }

    override fun onCreate(db: SupportSQLiteDatabase) = with(db) {
        execSQL(MangaTable.createTableQuery)
        execSQL(ChapterTable.createTableQuery)
        execSQL(TrackTable.createTableQuery)
        execSQL(CategoryTable.createTableQuery)
        execSQL(MangaCategoryTable.createTableQuery)
        execSQL(HistoryTable.createTableQuery)
        // EXH -->
        execSQL(SearchMetadataTable.createTableQuery)
        execSQL(SearchTagTable.createTableQuery)
        execSQL(SearchTitleTable.createTableQuery)
        // EXH <--
        // AZ -->
        execSQL(MergedTable.createTableQuery)
        // AZ <--

        // DB indexes
        execSQL(MangaTable.createUrlIndexQuery)
        execSQL(MangaTable.createLibraryIndexQuery)
        execSQL(ChapterTable.createMangaIdIndexQuery)
        execSQL(ChapterTable.createUnreadChaptersIndexQuery)
        execSQL(HistoryTable.createChapterIdIndexQuery)
        // EXH -->
        db.execSQL(SearchMetadataTable.createUploaderIndexQuery)
        db.execSQL(SearchMetadataTable.createIndexedExtraIndexQuery)
        db.execSQL(SearchTagTable.createMangaIdIndexQuery)
        db.execSQL(SearchTagTable.createNamespaceNameIndexQuery)
        db.execSQL(SearchTitleTable.createMangaIdIndexQuery)
        db.execSQL(SearchTitleTable.createTitleIndexQuery)
        // EXH <--
        // AZ -->
        execSQL(MergedTable.createIndexQuery)
        // AZ <--
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 0) {
            db.execSQL(ChapterTable.sourceOrderUpdateQuery)

            // Fix kissmanga covers after supporting cloudflare
            db.execSQL(
                """UPDATE mangas SET thumbnail_url =
                    REPLACE(thumbnail_url, '93.174.95.110', 'kissmanga.com') WHERE source = 4"""
            )
        }
    }

    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
}
