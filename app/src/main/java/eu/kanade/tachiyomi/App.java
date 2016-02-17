package eu.kanade.tachiyomi;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;
import org.greenrobot.eventbus.EventBus;

import eu.kanade.tachiyomi.injection.ComponentReflectionInjector;
import eu.kanade.tachiyomi.injection.component.AppComponent;
import eu.kanade.tachiyomi.injection.component.DaggerAppComponent;
import eu.kanade.tachiyomi.injection.module.AppModule;
import timber.log.Timber;

@ReportsCrashes(
        formUri = "http://tachiyomi.kanade.eu/crash_report",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        excludeMatchingSharedPreferencesKeys={".*username.*",".*password.*"}
)
public class App extends Application {

    AppComponent applicationComponent;
    ComponentReflectionInjector<AppComponent> componentInjector;

    public static App get(Context context) {
        return (App) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        applicationComponent = prepareAppComponent().build();

        componentInjector =
                new ComponentReflectionInjector<>(AppComponent.class, applicationComponent);

        setupEventBus();
        setupAcra();
    }

    protected DaggerAppComponent.Builder prepareAppComponent() {
        return DaggerAppComponent.builder()
                .appModule(new AppModule(this));
    }

    protected void setupEventBus() {
        EventBus.builder()
                .addIndex(new EventBusIndex())
                .logNoSubscriberMessages(false)
                .installDefaultEventBus();
    }

    protected void setupAcra() {
        ACRA.init(this);
    }

    public AppComponent getComponent() {
        return applicationComponent;
    }

    public ComponentReflectionInjector<AppComponent> getComponentReflection() {
        return componentInjector;
    }
}
