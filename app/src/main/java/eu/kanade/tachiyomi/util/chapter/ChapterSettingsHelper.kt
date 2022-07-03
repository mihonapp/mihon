package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.domain.manga.interactor.SetMangaChapterFlags
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.lang.launchIO
import uy.kohesive.injekt.injectLazy

object ChapterSettingsHelper {

    private val prefs: PreferencesHelper by injectLazy()
    private val getFavorites: GetFavorites by injectLazy()
    private val setMangaChapterFlags: SetMangaChapterFlags by injectLazy()

    /**
     * Updates the global Chapter Settings in Preferences.
     */
    fun setGlobalSettings(manga: Manga) {
        prefs.setChapterSettingsDefault(manga.toDbManga())
    }

    /**
     * Updates a single manga's Chapter Settings to match what's set in Preferences.
     */
    fun applySettingDefaults(manga: Manga) {
        launchIO {
            setMangaChapterFlags.awaitSetAllFlags(
                mangaId = manga.id,
                unreadFilter = prefs.filterChapterByRead().toLong(),
                downloadedFilter = prefs.filterChapterByDownloaded().toLong(),
                bookmarkedFilter = prefs.filterChapterByBookmarked().toLong(),
                sortingMode = prefs.sortChapterBySourceOrNumber().toLong(),
                sortingDirection = prefs.sortChapterByAscendingOrDescending().toLong(),
                displayMode = prefs.displayChapterByNameOrNumber().toLong(),
            )
        }
    }

    suspend fun applySettingDefaults(mangaId: Long) {
        setMangaChapterFlags.awaitSetAllFlags(
            mangaId = mangaId,
            unreadFilter = prefs.filterChapterByRead().toLong(),
            downloadedFilter = prefs.filterChapterByDownloaded().toLong(),
            bookmarkedFilter = prefs.filterChapterByBookmarked().toLong(),
            sortingMode = prefs.sortChapterBySourceOrNumber().toLong(),
            sortingDirection = prefs.sortChapterByAscendingOrDescending().toLong(),
            displayMode = prefs.displayChapterByNameOrNumber().toLong(),
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
                        unreadFilter = prefs.filterChapterByRead().toLong(),
                        downloadedFilter = prefs.filterChapterByDownloaded().toLong(),
                        bookmarkedFilter = prefs.filterChapterByBookmarked().toLong(),
                        sortingMode = prefs.sortChapterBySourceOrNumber().toLong(),
                        sortingDirection = prefs.sortChapterByAscendingOrDescending().toLong(),
                        displayMode = prefs.displayChapterByNameOrNumber().toLong(),
                    )
                }
        }
    }
}
