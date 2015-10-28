package eu.kanade.mangafeed.injection.module;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.NetworkHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
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

    @Provides
    @Singleton
    CacheManager provideCacheManager(Application app) {
        return new CacheManager(app);
    }

    @Provides
    @Singleton
    NetworkHelper provideNetworkHelper() {
        return new NetworkHelper();
    }

    @Provides
    @Singleton
    SourceManager provideSourceManager(Application app) {
        return new SourceManager(app);
    }

}