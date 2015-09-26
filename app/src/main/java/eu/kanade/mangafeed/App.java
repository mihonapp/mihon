package eu.kanade.mangafeed;

import android.app.Application;
import android.content.Context;

import timber.log.Timber;

public class App extends Application {

    AppComponent mApplicationComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        mApplicationComponent = DaggerAppComponent.builder()
                .appModule(new AppModule(this))
                .build();
    }

    public static App get(Context context) {
        return (App) context.getApplicationContext();
    }

    public AppComponent getComponent() {
        return mApplicationComponent;
    }

    public static AppComponent getComponent(Context context) {
        return get(context).getComponent();
    }

    // Needed to replace the component with a test specific one
    public void setComponent(AppComponent applicationComponent) {
        mApplicationComponent = applicationComponent;
    }
}
