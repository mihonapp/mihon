package tachiyomi.data

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import mihon.data.database.DatabaseRepositoryImpl

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    /**
     * WAL on every device, so a write never blocks reads. Low-RAM devices get one reader instead of four, since each
     * connection keeps its own page cache.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(context: Context, databaseRepository: DatabaseRepositoryImpl): SqlDriver {
        val isLowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
        return AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.db"),
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
            ),
            // Runs before the driver compares the schema version, and onOpen once the schema is ready
            onConfigure = {
                val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                databaseRepository.isMigrating.value = version in 1..<Database.Schema.version
            },
            onOpen = {
                databaseRepository.isMigrating.value = false
            },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = DateColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_memoAdapter = MemoColumnAdapter,
            ),
        )
    }
}
