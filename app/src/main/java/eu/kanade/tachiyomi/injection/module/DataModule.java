package eu.kanade.tachiyomi.injection.module;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import eu.kanade.tachiyomi.data.cache.ChapterCache;
import eu.kanade.tachiyomi.data.cache.CoverCache;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.download.DownloadManager;
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager;
import eu.kanade.tachiyomi.data.network.NetworkHelper;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.SourceManager;

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
    ChapterCache provideChapterCache(Application app) {
        return new ChapterCache(app);
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
    MangaSyncManager provideMangaSyncManager(Application app) {
        return new MangaSyncManager(app);
    }

}