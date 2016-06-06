package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.injection.AppComponentFactory
import eu.kanade.tachiyomi.injection.ComponentReflectionInjector
import eu.kanade.tachiyomi.injection.component.AppComponent
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

        component = createAppComponent()

        componentReflection = ComponentReflectionInjector(AppComponent::class.java, component)

        setupTheme()
        setupAcra()
    }

    private fun setupTheme() {
        appTheme = PreferencesHelper.getTheme(this)
    }

    protected open fun createAppComponent(): AppComponent {
        return AppComponentFactory.create(this)
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
