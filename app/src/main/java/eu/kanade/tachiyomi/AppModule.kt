package eu.kanade.tachiyomi

import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.isDevFlavor
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.preference.AndroidPreferenceStore
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.provider.AndroidBackupFolderProvider
import tachiyomi.core.provider.AndroidDownloadFolderProvider
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory<SqlDriver> {
            AndroidSqliteDriver(
                schema = Database.Schema,
                context = app,
                name = "tachiyomi.db",
                factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Support database inspector in Android Studio
                    FrameworkSQLiteOpenHelperFactory()
                } else {
                    RequerySQLiteOpenHelperFactory()
                },
                callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        setPragma(db, "foreign_keys = ON")
                        setPragma(db, "journal_mode = WAL")
                        setPragma(db, "synchronous = NORMAL")
                    }
                    private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                        val cursor = db.query("PRAGMA $pragma")
                        cursor.moveToFirst()
                        cursor.close()
                    }
                },
            )
        }
        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                ),
            )
        }
        addSingletonFactory<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }

        addSingletonFactory { ChapterCache(app) }
        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<SourceManager> { AndroidSourceManager(app, get(), get()) }
        addSingletonFactory { ExtensionManager(app) }

        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { DownloadCache(app) }

        addSingletonFactory { TrackManager(app) }
        addSingletonFactory { DelayedTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { LocalSourceFileSystem(app) }
        addSingletonFactory { LocalCoverManager(app, get()) }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DownloadManager>()
        }
    }
}

class PreferenceModule(val application: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(application)
        }
        addSingletonFactory {
            NetworkPreferences(
                preferenceStore = get(),
                verboseLogging = isDevFlavor,
            )
        }
        addSingletonFactory {
            SourcePreferences(get())
        }
        addSingletonFactory {
            SecurityPreferences(get())
        }
        addSingletonFactory {
            LibraryPreferences(get())
        }
        addSingletonFactory {
            ReaderPreferences(get())
        }
        addSingletonFactory {
            TrackPreferences(get())
        }
        addSingletonFactory {
            AndroidDownloadFolderProvider(application)
        }
        addSingletonFactory {
            DownloadPreferences(
                folderProvider = get<AndroidDownloadFolderProvider>(),
                preferenceStore = get(),
            )
        }
        addSingletonFactory {
            AndroidBackupFolderProvider(application)
        }
        addSingletonFactory {
            BackupPreferences(
                folderProvider = get<AndroidBackupFolderProvider>(),
                preferenceStore = get(),
            )
        }
        addSingletonFactory {
            UiPreferences(get())
        }
        addSingletonFactory {
            BasePreferences(application, get())
        }
    }
}
