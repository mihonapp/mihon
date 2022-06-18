package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.sqldelight.android.AndroidSqliteDriver
import eu.kanade.tachiyomi.Database
import logcat.logcat

class DbOpenCallback : SupportSQLiteOpenHelper.Callback(Database.Schema.version) {

    companion object {
        const val DATABASE_FILENAME = "tachiyomi.db"
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        logcat { "Creating new database" }
        Database.Schema.create(AndroidSqliteDriver(database = db, cacheSize = 1))
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < newVersion) {
            logcat { "Upgrading database from $oldVersion to $newVersion" }
            Database.Schema.migrate(
                driver = AndroidSqliteDriver(database = db, cacheSize = 1),
                oldVersion = oldVersion,
                newVersion = newVersion,
            )
        }
    }

    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
}
