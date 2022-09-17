package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.domain.manga.interactor.SetMangaChapterFlags
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.lang.launchIO
import uy.kohesive.injekt.injectLazy

object ChapterSettingsHelper {

    private val preferences: PreferencesHelper by injectLazy()
    private val getFavorites: GetFavorites by injectLazy()
    private val setMangaChapterFlags: SetMangaChapterFlags by injectLazy()

    /**
     * Updates the global Chapter Settings in Preferences.
     */
    fun setGlobalSettings(manga: Manga) {
        preferences.setChapterSettingsDefault(manga.toDbManga())
    }

    /**
     * Updates a single manga's Chapter Settings to match what's set in Preferences.
     */
    suspend fun applySettingDefaults(mangaId: Long) {
        setMangaChapterFlags.awaitSetAllFlags(
            mangaId = mangaId,
            unreadFilter = preferences.filterChapterByRead().get().toLong(),
            downloadedFilter = preferences.filterChapterByDownloaded().get().toLong(),
            bookmarkedFilter = preferences.filterChapterByBookmarked().get().toLong(),
            sortingMode = preferences.sortChapterBySourceOrNumber().get().toLong(),
            sortingDirection = preferences.sortChapterByAscendingOrDescending().get().toLong(),
            displayMode = preferences.displayChapterByNameOrNumber().get().toLong(),
        )
    }

    /**
     * Updates all mangas in library with global Chapter Settings.
     */
    fun updateAllMangasWithGlobalDefaults() {
        launchIO {
            getFavorites.await()
                .map { manga ->
                    setMangaChapterFlags.awaitSetAllFlags(
                        mangaId = manga.id,
                        unreadFilter = preferences.filterChapterByRead().get().toLong(),
                        downloadedFilter = preferences.filterChapterByDownloaded().get().toLong(),
                        bookmarkedFilter = preferences.filterChapterByBookmarked().get().toLong(),
                        sortingMode = preferences.sortChapterBySourceOrNumber().get().toLong(),
                        sortingDirection = preferences.sortChapterByAscendingOrDescending().get().toLong(),
                        displayMode = preferences.displayChapterByNameOrNumber().get().toLong(),
                    )
                }
        }
    }
}
