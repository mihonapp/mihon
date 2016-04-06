package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.injection.ComponentReflectionInjector
import eu.kanade.tachiyomi.injection.component.AppComponent
import eu.kanade.tachiyomi.injection.component.DaggerAppComponent
import eu.kanade.tachiyomi.injection.module.AppModule
import org.acra.ACRA
import org.acra.annotation.ReportsCrashes
import timber.log.Timber

@ReportsCrashes(
        formUri = "http://tachiyomi.kanade.eu/crash_report",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        buildConfigClass = BuildConfig::class,
        excludeMatchingSharedPreferencesKeys = arrayOf(".*username.*", ".*password.*")
)
open class App : Application() {

    lateinit var component: AppComponent
        private set

    lateinit var componentReflection: ComponentReflectionInjector<AppComponent>
        private set

    var appTheme = 0

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        component = prepareAppComponent().build()

        componentReflection = ComponentReflectionInjector(AppComponent::class.java, component)

        setupTheme()
        setupAcra()
    }

    private fun setupTheme() {
        appTheme = PreferencesHelper.getTheme(this)
    }

    protected open fun prepareAppComponent(): DaggerAppComponent.Builder {
        return DaggerAppComponent.builder()
                .appModule(AppModule(this))
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
