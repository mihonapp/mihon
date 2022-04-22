package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.sqldelight.android.AndroidSqliteDriver
import eu.kanade.tachiyomi.Database

class DbOpenCallback : SupportSQLiteOpenHelper.Callback(Database.Schema.version) {

    companion object {
        /**
         * Name of the database file.
         */
        const val DATABASE_NAME = "tachiyomi.db"
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        Database.Schema.create(AndroidSqliteDriver(database = db, cacheSize = 1))
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Database.Schema.migrate(
            driver = AndroidSqliteDriver(database = db, cacheSize = 1),
            oldVersion = oldVersion,
            newVersion = newVersion
        )
    }

    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
}
