package tachiyomi.domain.library.service

import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.getEnum
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.TriStateFilter

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun libraryDisplayMode() = preferenceStore.getObject("pref_display_mode_library", LibraryDisplayMode.default, LibraryDisplayMode.Serializer::serialize, LibraryDisplayMode.Serializer::deserialize)

    fun librarySortingMode() = preferenceStore.getObject("library_sorting_mode", LibrarySort.default, LibrarySort.Serializer::serialize, LibrarySort.Serializer::deserialize)

    fun portraitColumns() = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    fun libraryUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 0)
    fun libraryUpdateLastTimestamp() = preferenceStore.getLong("library_update_last_timestamp", 0L)

    fun libraryUpdateDeviceRestriction() = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    fun libraryUpdateMangaRestriction() = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            MANGA_HAS_UNREAD,
            MANGA_NON_COMPLETED,
            MANGA_NON_READ,
        ),
    )

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun autoUpdateTrackers() = preferenceStore.getBoolean("auto_update_trackers", false)

    fun showContinueReadingButton() = preferenceStore.getBoolean("display_continue_reading_button", false)

    // region Filter

    fun filterDownloaded() = preferenceStore.getEnum("pref_filter_library_downloaded_v2", TriStateFilter.DISABLED)

    fun filterUnread() = preferenceStore.getEnum("pref_filter_library_unread_v2", TriStateFilter.DISABLED)

    fun filterStarted() = preferenceStore.getEnum("pref_filter_library_started_v2", TriStateFilter.DISABLED)

    fun filterBookmarked() = preferenceStore.getEnum("pref_filter_library_bookmarked_v2", TriStateFilter.DISABLED)

    fun filterCompleted() = preferenceStore.getEnum("pref_filter_library_completed_v2", TriStateFilter.DISABLED)

    fun filterTracking(id: Int) = preferenceStore.getEnum("pref_filter_library_tracked_${id}_v2", TriStateFilter.DISABLED)

    // endregion

    // region Badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", false)

    fun newShowUpdatesCount() = preferenceStore.getBoolean("library_show_updates_count", true)
    fun newUpdatesCount() = preferenceStore.getInt("library_unseen_updates_count", 0)

    // endregion

    // region Category

    fun defaultCategory() = preferenceStore.getInt("default_category", -1)

    fun lastUsedCategory() = preferenceStore.getInt("last_used_category", 0)

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    fun libraryUpdateCategories() = preferenceStore.getStringSet("library_update_categories", emptySet())

    fun libraryUpdateCategoriesExclude() = preferenceStore.getStringSet("library_update_categories_exclude", emptySet())

    // endregion

    // region Chapter

    fun filterChapterByRead() = preferenceStore.getLong("default_chapter_filter_by_read", Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = preferenceStore.getLong("default_chapter_filter_by_downloaded", Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = preferenceStore.getLong("default_chapter_filter_by_bookmarked", Manga.SHOW_ALL)

    // and upload date
    fun sortChapterBySourceOrNumber() = preferenceStore.getLong("default_chapter_sort_by_source_or_number", Manga.CHAPTER_SORTING_SOURCE)

    fun displayChapterByNameOrNumber() = preferenceStore.getLong("default_chapter_display_by_name_or_number", Manga.CHAPTER_DISPLAY_NAME)

    fun sortChapterByAscendingOrDescending() = preferenceStore.getLong("default_chapter_sort_by_ascending_or_descending", Manga.CHAPTER_SORT_DESC)

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.unreadFilterRaw)
        filterChapterByDownloaded().set(manga.downloadedFilterRaw)
        filterChapterByBookmarked().set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
    }

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    // endregion

    // region Swipe Actions

    fun swipeEndAction() = preferenceStore.getEnum("pref_chapter_swipe_end_action", ChapterSwipeAction.ToggleBookmark)

    fun swipeStartAction() = preferenceStore.getEnum("pref_chapter_swipe_start_action", ChapterSwipeAction.ToggleRead)

    // endregion

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"
        const val DEVICE_BATTERY_NOT_LOW = "battery_not_low"

        const val MANGA_NON_COMPLETED = "manga_ongoing"
        const val MANGA_HAS_UNREAD = "manga_fully_read"
        const val MANGA_NON_READ = "manga_started"
    }
}
