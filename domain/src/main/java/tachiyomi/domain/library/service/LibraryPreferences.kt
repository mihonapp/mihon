package tachiyomi.domain.library.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.manga.model.Manga

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun displayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun sortingMode() = preferenceStore.getObjectFromString(
        "library_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    fun randomSortSeed() = preferenceStore.getInt("library_random_sort_seed", 0)

    fun portraitColumns() = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    fun titleMaxLines() = preferenceStore.getInt("pref_library_title_max_lines", 2)

    fun lastUpdatedTimestamp() = preferenceStore.getLong(Preference.appStateKey("library_update_last_timestamp"), 0L)
    fun autoUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 0)

    fun autoUpdateDeviceRestrictions() = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    fun autoUpdateMangaRestrictions() = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            MANGA_HAS_UNREAD,
            MANGA_NON_COMPLETED,
            MANGA_NON_READ,
            MANGA_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    fun skipUpdateTime() = preferenceStore.getInt("pref_skip_update_time", SKIP_UPDATE_NONE)

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun autoUpdateThrottle() = preferenceStore.getInt("pref_library_update_throttle_ms", 0)

    fun showContinueReadingButton() = preferenceStore.getBoolean(
        "display_continue_reading_button",
        false,
    )

    fun markDuplicateReadChapterAsRead() = preferenceStore.getStringSet("mark_duplicate_read_chapter_read", emptySet())

    // region Filter

    fun filterDownloaded() = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    fun filterUnread() = preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    fun filterStarted() = preferenceStore.getEnum(
        "pref_filter_library_started_v2",
        TriState.DISABLED,
    )

    fun filterBookmarked() = preferenceStore.getEnum(
        "pref_filter_library_bookmarked_v2",
        TriState.DISABLED,
    )

    fun filterCompleted() = preferenceStore.getEnum(
        "pref_filter_library_completed_v2",
        TriState.DISABLED,
    )

    fun filterNovel() = preferenceStore.getEnum(
        "pref_filter_library_novel",
        TriState.DISABLED,
    )

    fun filterIntervalCustom() = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    fun filterTracking(id: Int) = preferenceStore.getEnum(
        "pref_filter_library_tracked_${id}_v2",
        TriState.DISABLED,
    )

    fun filterExtensions() = preferenceStore.getStringSet("pref_filter_library_extensions", emptySet())

    // Stores extension IDs that are excluded (unchecked) from the library filter
    fun excludedExtensions() = preferenceStore.getStringSet("pref_excluded_library_extensions", emptySet())

    // Stores source IDs that should have their chapter list reversed
    fun reversedChapterSources() = preferenceStore.getStringSet("pref_reversed_chapter_sources", emptySet())

    // Tag filtering - included tags (TriState: DISABLED = show all, ENABLED_IS = include, ENABLED_NOT = exclude)
    fun includedTags() = preferenceStore.getStringSet("pref_filter_library_included_tags", emptySet())
    fun excludedTags() = preferenceStore.getStringSet("pref_filter_library_excluded_tags", emptySet())
    fun filterNoTags() = preferenceStore.getEnum("pref_filter_library_no_tags", TriState.DISABLED)

    // Tag filter logic modes (true = AND, false = OR)
    fun tagIncludeMode() = preferenceStore.getBoolean("pref_tag_include_mode_and", false) // Default OR
    fun tagExcludeMode() = preferenceStore.getBoolean("pref_tag_exclude_mode_and", false) // Default OR

    // Tag sort preferences
    fun tagSortByName() = preferenceStore.getBoolean("pref_tag_sort_by_name", false) // Default sort by count
    fun tagSortAscending() = preferenceStore.getBoolean("pref_tag_sort_ascending", false) // Default descending

    // Tag case sensitivity (default insensitive)
    fun tagCaseSensitive() = preferenceStore.getBoolean("pref_tag_case_sensitive", false)

    // Manga detail page tag sorting (true = alphabetical, false = source order)
    fun sortMangaTags() = preferenceStore.getBoolean("pref_sort_manga_tags", false)

    // Search options - what to include in library search
    fun searchChapterNames() = preferenceStore.getBoolean("pref_search_chapter_names", false)
    fun searchChapterContent() = preferenceStore.getBoolean("pref_search_chapter_content", false)
    fun searchByUrl() = preferenceStore.getBoolean("pref_search_by_url", false)
    fun useRegexSearch() = preferenceStore.getBoolean("pref_use_regex_search", false)

    // endregion

    // region Badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun unreadBadge() = preferenceStore.getBoolean("display_unread_badge", true)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", false)

    fun showUrlInList() = preferenceStore.getBoolean("display_url_in_list", false)

    fun newShowUpdatesCount() = preferenceStore.getBoolean("library_show_updates_count", true)
    fun newUpdatesCount() = preferenceStore.getInt(Preference.appStateKey("library_unseen_updates_count"), 0)

    // endregion

    // region Category

    fun defaultCategory() = preferenceStore.getInt(DEFAULT_CATEGORY_PREF_KEY, -1)

    fun lastUsedCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    fun updateCategories() = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_PREF_KEY, emptySet())

    fun updateCategoriesExclude() = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    // endregion

    // region Chapter

    fun filterChapterByRead() = preferenceStore.getLong(
        "default_chapter_filter_by_read",
        Manga.SHOW_ALL,
    )

    fun filterChapterByDownloaded() = preferenceStore.getLong(
        "default_chapter_filter_by_downloaded",
        Manga.SHOW_ALL,
    )

    fun filterChapterByBookmarked() = preferenceStore.getLong(
        "default_chapter_filter_by_bookmarked",
        Manga.SHOW_ALL,
    )

    // and upload date
    fun sortChapterBySourceOrNumber() = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    fun displayChapterByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_BOTH,
    )

    fun sortChapterByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.unreadFilterRaw)
        filterChapterByDownloaded().set(manga.downloadedFilterRaw)
        filterChapterByBookmarked().set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    fun hideMissingChapters() = preferenceStore.getBoolean("pref_hide_missing_chapter_indicators", false)

    /**
     * Whether the library should auto-refresh when database changes occur.
     * Disabling this improves performance on large libraries by requiring manual refresh.
     */
    fun autoRefreshLibrary() = preferenceStore.getBoolean("pref_auto_refresh_library", true)

    /**
     * Whether to verify library cache integrity on app startup.
     * Disabling this speeds up startup but may result in stale cache data.
     */
    fun verifyCacheOnStartup() = preferenceStore.getBoolean("pref_verify_cache_on_startup", true)
    // endregion

    // region Swipe Actions

    fun swipeToStartAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_end_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    fun swipeToEndAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleRead,
    )

    fun updateMangaTitles() = preferenceStore.getBoolean("pref_update_library_manga_titles", false)

    fun disallowNonAsciiFilenames() = preferenceStore.getBoolean("disallow_non_ascii_filenames", false)

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

        const val MANGA_NON_COMPLETED = "manga_ongoing"
        const val MANGA_HAS_UNREAD = "manga_fully_read"
        const val MANGA_NON_READ = "manga_started"
        const val MANGA_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"

        const val SKIP_UPDATE_NONE = 0
        const val SKIP_UPDATE_1_DAY = 1
        const val SKIP_UPDATE_3_DAYS = 3
        const val SKIP_UPDATE_7_DAYS = 7
        const val SKIP_UPDATE_14_DAYS = 14
        const val SKIP_UPDATE_30_DAYS = 30
        const val SKIP_UPDATE_60_DAYS = 60
        const val SKIP_UPDATE_90_DAYS = 90

        const val MARK_DUPLICATE_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_CHAPTER_READ_EXISTING = "existing"

        const val DEFAULT_CATEGORY_PREF_KEY = "default_category"
        private const val LIBRARY_UPDATE_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"
        val categoryPreferenceKeys = setOf(
            DEFAULT_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
