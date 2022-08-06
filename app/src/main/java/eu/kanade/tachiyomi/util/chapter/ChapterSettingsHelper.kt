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
    fun applySettingDefaults(manga: Manga) {
        launchIO {
            setMangaChapterFlags.awaitSetAllFlags(
                mangaId = manga.id,
                unreadFilter = preferences.filterChapterByRead().toLong(),
                downloadedFilter = preferences.filterChapterByDownloaded().toLong(),
                bookmarkedFilter = preferences.filterChapterByBookmarked().toLong(),
                sortingMode = preferences.sortChapterBySourceOrNumber().toLong(),
                sortingDirection = preferences.sortChapterByAscendingOrDescending().toLong(),
                displayMode = preferences.displayChapterByNameOrNumber().toLong(),
            )
        }
    }

    suspend fun applySettingDefaults(mangaId: Long) {
        setMangaChapterFlags.awaitSetAllFlags(
            mangaId = mangaId,
            unreadFilter = preferences.filterChapterByRead().toLong(),
            downloadedFilter = preferences.filterChapterByDownloaded().toLong(),
            bookmarkedFilter = preferences.filterChapterByBookmarked().toLong(),
            sortingMode = preferences.sortChapterBySourceOrNumber().toLong(),
            sortingDirection = preferences.sortChapterByAscendingOrDescending().toLong(),
            displayMode = preferences.displayChapterByNameOrNumber().toLong(),
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
                        unreadFilter = preferences.filterChapterByRead().toLong(),
                        downloadedFilter = preferences.filterChapterByDownloaded().toLong(),
                        bookmarkedFilter = preferences.filterChapterByBookmarked().toLong(),
                        sortingMode = preferences.sortChapterBySourceOrNumber().toLong(),
                        sortingDirection = preferences.sortChapterByAscendingOrDescending().toLong(),
                        displayMode = preferences.displayChapterByNameOrNumber().toLong(),
                    )
                }
        }
    }
}
