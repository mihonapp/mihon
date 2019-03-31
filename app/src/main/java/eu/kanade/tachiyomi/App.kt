package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.support.multidex.MultiDex
import com.evernote.android.job.JobManager
import com.github.ajalt.reprint.core.Reprint
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.util.LocaleHelper
import io.realm.Realm
import io.realm.RealmConfiguration
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import java.io.File
import kotlin.concurrent.thread

open class App : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        Injekt = InjektScope(DefaultRegistrar())
        Injekt.importModule(AppModule(this))

        setupJobManager()
        setupNotificationChannels()
        setupRealm() //Setup metadata DB (EH)
        Reprint.initialize(this) //Setup fingerprint (EH)

        LocaleHelper.updateConfiguration(this, resources.configuration)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (BuildConfig.DEBUG) {
            MultiDex.install(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.updateConfiguration(this, newConfig, true)
    }

    protected open fun setupJobManager() {
        try {
            JobManager.create(this).addJobCreator { tag ->
                when (tag) {
                    LibraryUpdateJob.TAG -> LibraryUpdateJob()
                    UpdaterJob.TAG -> UpdaterJob()
                    BackupCreatorJob.TAG -> BackupCreatorJob()
                    else -> null
                }
            }
        } catch (e: Exception) {
            Timber.w("Can't initialize job manager")
        }
    }

    protected open fun setupNotificationChannels() {
        Notifications.createChannels(this)
    }

    // EXH
    private fun setupRealm() {
        Realm.init(this)
        val config = RealmConfiguration.Builder()
                .name("gallery-metadata.realm")
                .schemaVersion(3)
                .deleteRealmIfMigrationNeeded()
                .build()
        Realm.setDefaultConfiguration(config)

        //Delete old paper db files
        listOf(
                File(filesDir, "gallery-ex"),
                File(filesDir, "gallery-perveden"),
                File(filesDir, "gallery-nhentai")
        ).forEach {
            if(it.exists()) {
                thread {
                    it.deleteRecursively()
                }
            }
        }
    }
}
