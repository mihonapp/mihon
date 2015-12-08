package eu.kanade.mangafeed;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import eu.kanade.mangafeed.injection.ComponentReflectionInjector;
import eu.kanade.mangafeed.injection.component.AppComponent;
import eu.kanade.mangafeed.injection.component.DaggerAppComponent;
import eu.kanade.mangafeed.injection.module.AppModule;
import timber.log.Timber;

@ReportsCrashes(
        formUri = "http://mangafeed.kanade.eu/crash_report",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        excludeMatchingSharedPreferencesKeys={".*username.*",".*password.*"}
)
public class App extends Application {

    AppComponent applicationComponent;
    ComponentReflectionInjector<AppComponent> componentInjector;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        applicationComponent = DaggerAppComponent.builder()
                .appModule(new AppModule(this))
                .build();

        componentInjector =
                new ComponentReflectionInjector<>(AppComponent.class, applicationComponent);

        ACRA.init(this);
    }

    public static App get(Context context) {
        return (App) context.getApplicationContext();
    }

    public AppComponent getComponent() {
        return applicationComponent;
    }

    public ComponentReflectionInjector<AppComponent> getComponentReflection() {
        return componentInjector;
    }

    // Needed to replace the component with a test specific one
    public void setComponent(AppComponent applicationComponent) {
        this.applicationComponent = applicationComponent;
    }
}
