package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the keys for the preferences in the application.
 */
object PreferenceKeys {

    const val confirmExit = "pref_confirm_exit"

    const val showReadingMode = "pref_show_reading_mode"

    const val defaultReadingMode = "pref_default_reading_mode_key"

    const val defaultOrientationType = "pref_default_orientation_type_key"

    const val jumpToChapters = "jump_to_chapters"

    const val autoUpdateTrack = "pref_auto_update_manga_sync_key"

    const val downloadOnlyOverWifi = "pref_download_only_over_wifi_key"

    const val folderPerManga = "create_folder_per_manga"

    const val removeAfterReadSlots = "remove_after_read_slots"

    const val removeAfterMarkedAsRead = "pref_remove_after_marked_as_read_key"

    const val removeBookmarkedChapters = "pref_remove_bookmarked"

    const val filterDownloaded = "pref_filter_library_downloaded"

    const val filterUnread = "pref_filter_library_unread"

    const val filterCompleted = "pref_filter_library_completed"

    const val filterTracked = "pref_filter_library_tracked"

    const val librarySortingMode = "library_sorting_mode"
    const val librarySortingDirection = "library_sorting_ascending"

    const val migrationSortingMode = "pref_migration_sorting"
    const val migrationSortingDirection = "pref_migration_direction"

    const val startScreen = "start_screen"

    const val hideNotificationContent = "hide_notification_content"

    const val autoUpdateMetadata = "auto_update_metadata"

    const val autoUpdateTrackers = "auto_update_trackers"

    const val dateFormat = "app_date_format"

    const val defaultCategory = "default_category"

    const val skipRead = "skip_read"

    const val skipFiltered = "skip_filtered"

    const val searchPinnedSourcesOnly = "search_pinned_sources_only"

    const val dohProvider = "doh_provider"

    const val defaultChapterFilterByRead = "default_chapter_filter_by_read"

    const val defaultChapterFilterByDownloaded = "default_chapter_filter_by_downloaded"

    const val defaultChapterFilterByBookmarked = "default_chapter_filter_by_bookmarked"

    const val defaultChapterSortBySourceOrNumber = "default_chapter_sort_by_source_or_number" // and upload date

    const val defaultChapterSortByAscendingOrDescending = "default_chapter_sort_by_ascending_or_descending"

    const val defaultChapterDisplayByNameOrNumber = "default_chapter_display_by_name_or_number"

    const val verboseLogging = "verbose_logging"

    const val autoClearChapterCache = "auto_clear_chapter_cache"

    fun trackUsername(syncId: Int) = "pref_mangasync_username_$syncId"

    fun trackPassword(syncId: Int) = "pref_mangasync_password_$syncId"

    fun trackToken(syncId: Int) = "track_token_$syncId"
}
