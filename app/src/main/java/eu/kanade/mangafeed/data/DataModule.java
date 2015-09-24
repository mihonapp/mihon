package eu.kanade.mangafeed.data;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * Provide dependencies to the DataManager, mainly Helper classes and Retrofit services.
 */
@Module
public class DataModule {

    @Provides
    @Singleton
    PreferencesHelper providePreferencesHelper(Application app) {
        return new PreferencesHelper(app);
    }

    @Provides
    @Singleton
    DatabaseHelper provideDatabaseHelper(Application app) {
        return new DatabaseHelper(app);
    }

    @Provides
    @Singleton
    Scheduler provideSubscribeScheduler() {
        return Schedulers.io();
    }

}