package eu.kanade.tachiyomi

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        Injekt.importModule(AppModule(this))
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        setupAcra()
    }

    protected open fun setupAcra() {
        ACRA.init(this)
    }

}
