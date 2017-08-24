package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.support.multidex.MultiDex
import com.evernote.android.job.JobManager
import com.github.ajalt.reprint.core.Reprint
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.updater.UpdateCheckerJob
import eu.kanade.tachiyomi.util.LocaleHelper
import io.paperdb.Paper
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.registry.default.DefaultRegistrar

open class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Injekt = InjektScope(DefaultRegistrar())
        Injekt.importModule(AppModule(this))

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        setupJobManager()
        Paper.init(this) //Setup metadata DB (EH)
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
        JobManager.create(this).addJobCreator { tag ->
            when (tag) {
                LibraryUpdateJob.TAG -> LibraryUpdateJob()
                UpdateCheckerJob.TAG -> UpdateCheckerJob()
                BackupCreatorJob.TAG -> BackupCreatorJob()
                else -> null
            }
        }
    }

}
