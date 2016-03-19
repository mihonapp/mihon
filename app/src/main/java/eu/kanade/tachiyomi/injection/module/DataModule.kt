package eu.kanade.tachiyomi.injection.module

import android.app.Application
import dagger.Module
import dagger.Provides
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.source.SourceManager
import javax.inject.Singleton

/**
 * Provide dependencies to the DataManager, mainly Helper classes and Retrofit services.
 */
@Module
open class DataModule {

    @Provides
    @Singleton
    fun providePreferencesHelper(app: Application): PreferencesHelper {
        return PreferencesHelper(app)
    }

    @Provides
    @Singleton
    open fun provideDatabaseHelper(app: Application): DatabaseHelper {
        return DatabaseHelper(app)
    }

    @Provides
    @Singleton
    fun provideChapterCache(app: Application): ChapterCache {
        return ChapterCache(app)
    }

    @Provides
    @Singleton
    fun provideCoverCache(app: Application): CoverCache {
        return CoverCache(app)
    }

    @Provides
    @Singleton
    open fun provideNetworkHelper(app: Application): NetworkHelper {
        return NetworkHelper(app)
    }

    @Provides
    @Singleton
    open fun provideSourceManager(app: Application): SourceManager {
        return SourceManager(app)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(app: Application, sourceManager: SourceManager, preferences: PreferencesHelper): DownloadManager {
        return DownloadManager(app, sourceManager, preferences)
    }

    @Provides
    @Singleton
    fun provideMangaSyncManager(app: Application): MangaSyncManager {
        return MangaSyncManager(app)
    }

}