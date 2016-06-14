package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import org.acra.ACRA
import org.acra.annotation.ReportsCrashes
import timber.log.Timber
import uy.kohesive.injekt.Injekt

@ReportsCrashes(
        formUri = "http://tachiyomi.kanade.eu/crash_report",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        buildConfigClass = BuildConfig::class,
        excludeMatchingSharedPreferencesKeys = arrayOf(".*username.*", ".*password.*")
)
open class App : Application() {

    var appTheme = 0

    override fun onCreate() {
        super.onCreate()
        Injekt.importModule(AppModule(this))
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        setupTheme()
        setupAcra()
    }

    private fun setupTheme() {
        appTheme = PreferencesHelper.getTheme(this)
    }

    protected open fun setupAcra() {
        ACRA.init(this)
    }

    companion object {
        @JvmStatic
        fun get(context: Context): App {
            return context.applicationContext as App
        }
    }
}
