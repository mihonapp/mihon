package eu.kanade.mangafeed.injection.module;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import eu.kanade.mangafeed.data.cache.CacheManager;
import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.chaptersync.ChapterSyncManager;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.download.DownloadManager;
import eu.kanade.mangafeed.data.network.NetworkHelper;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;

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
    CacheManager provideCacheManager(Application app, PreferencesHelper preferences) {
        return new CacheManager(app, preferences);
    }

    @Provides
    @Singleton
    CoverCache provideCoverCache(Application app) {
        return new CoverCache(app);
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

    @Provides
    @Singleton
    DownloadManager provideDownloadManager(
            Application app, SourceManager sourceManager, PreferencesHelper preferences) {
        return new DownloadManager(app, sourceManager, preferences);
    }

    @Provides
    @Singleton
    ChapterSyncManager provideChapterSyncManager(Application app) {
        return new ChapterSyncManager(app);
    }

}