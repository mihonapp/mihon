package eu.kanade.tachiyomi

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import data.History
import data.Mangas
import eu.kanade.data.AndroidDatabaseHandler
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.dateAdapter
import eu.kanade.data.listOfStringsAdapter
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.DbOpenCallback
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.job.DelayedTrackingStore
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        // This is used to allow incremental migration from Storio
        addSingletonFactory<SupportSQLiteOpenHelper> {
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(app)
                    .callback(DbOpenCallback())
                    .name(DbOpenCallback.DATABASE_NAME)
                    .noBackupDirectory(false)
                    .build(),
            )
        }

        addSingletonFactory<SqlDriver> {
            AndroidSqliteDriver(openHelper = get())
        }

        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    history_last_readAdapter = dateAdapter,
                    history_time_readAdapter = dateAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = listOfStringsAdapter,
                ),
            )
        }

        addSingletonFactory<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

        addSingletonFactory { Json { ignoreUnknownKeys = true } }

        addSingletonFactory { PreferencesHelper(app) }

        addSingletonFactory { DatabaseHelper(get()) }

        addSingletonFactory { ChapterCache(app) }

        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app) }

        addSingletonFactory { SourceManager(app).also { get<ExtensionManager>().init(it) } }

        addSingletonFactory { ExtensionManager(app) }

        addSingletonFactory { DownloadManager(app) }

        addSingletonFactory { TrackManager(app) }

        addSingletonFactory { DelayedTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<PreferencesHelper>()

            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DatabaseHelper>()

            get<DownloadManager>()
        }
    }
}
