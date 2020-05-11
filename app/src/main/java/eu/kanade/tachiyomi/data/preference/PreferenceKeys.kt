package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the keys for the preferences in the application.
 */
object PreferenceKeys {

    const val themeMode = "pref_theme_mode_key"

    const val themeLight = "pref_theme_light_key"

    const val themeDark = "pref_theme_dark_key"

    const val confirmExit = "pref_confirm_exit"

    const val rotation = "pref_rotation_type_key"

    const val enableTransitions = "pref_enable_transitions_key"

    const val doubleTapAnimationSpeed = "pref_double_tap_anim_speed"

    const val showPageNumber = "pref_show_page_number_key"

    const val trueColor = "pref_true_color_key"

    const val fullscreen = "fullscreen"

    const val cutoutShort = "cutout_short"

    const val keepScreenOn = "pref_keep_screen_on_key"

    const val customBrightness = "pref_custom_brightness_key"

    const val customBrightnessValue = "custom_brightness_value"

    const val colorFilter = "pref_color_filter_key"

    const val colorFilterValue = "color_filter_value"

    const val colorFilterMode = "color_filter_mode"

    const val defaultViewer = "pref_default_viewer_key"

    const val imageScaleType = "pref_image_scale_type_key"

    const val zoomStart = "pref_zoom_start_key"

    const val readerTheme = "pref_reader_theme_key"

    const val cropBorders = "crop_borders"

    const val cropBordersWebtoon = "crop_borders_webtoon"

    const val readWithTapping = "reader_tap"

    const val readWithLongTap = "reader_long_tap"

    const val readWithVolumeKeys = "reader_volume_keys"

    const val readWithVolumeKeysInverted = "reader_volume_keys_inverted"

    const val webtoonSidePadding = "webtoon_side_padding"

    const val portraitColumns = "pref_library_columns_portrait_key"

    const val landscapeColumns = "pref_library_columns_landscape_key"

    const val updateOnlyNonCompleted = "pref_update_only_non_completed_key"

    const val autoUpdateTrack = "pref_auto_update_manga_sync_key"

    const val lastUsedCatalogueSource = "last_catalogue_source"

    const val lastUsedCategory = "last_used_category"

    const val catalogueAsList = "pref_display_catalogue_as_list"

    const val enabledLanguages = "source_languages"

    const val sourcesSort = "sources_sort"

    const val backupDirectory = "backup_directory"

    const val downloadsDirectory = "download_directory"

    const val downloadOnlyOverWifi = "pref_download_only_over_wifi_key"

    const val numberOfBackups = "backup_slots"

    const val backupInterval = "backup_interval"

    const val removeAfterReadSlots = "remove_after_read_slots"

    const val removeAfterMarkedAsRead = "pref_remove_after_marked_as_read_key"

    const val libraryUpdateInterval = "pref_library_update_interval_key"

    const val libraryUpdateRestriction = "library_update_restriction"

    const val libraryUpdateCategories = "library_update_categories"

    const val libraryUpdatePrioritization = "library_update_prioritization"

    const val downloadedOnly = "pref_downloaded_only"

    const val filterDownloaded = "pref_filter_downloaded_key"

    const val filterUnread = "pref_filter_unread_key"

    const val filterCompleted = "pref_filter_completed_key"

    const val librarySortingMode = "library_sorting_mode"

    const val automaticExtUpdates = "automatic_ext_updates"

    const val startScreen = "start_screen"

    const val useBiometricLock = "use_biometric_lock"

    const val lockAppAfter = "lock_app_after"

    const val lastAppUnlock = "last_app_unlock"

    const val secureScreen = "secure_screen"

    const val hideNotificationContent = "hide_notification_content"

    const val downloadNew = "download_new"

    const val downloadNewCategories = "download_new_categories"

    const val libraryAsList = "pref_display_library_as_list"

    const val lang = "app_language"

    const val dateFormat = "app_date_format"

    const val defaultCategory = "default_category"

    const val skipRead = "skip_read"

    const val skipFiltered = "skip_filtered"

    const val downloadBadge = "display_download_badge"

    const val unreadBadge = "display_unread_badge"

    const val skipPreMigration = "skip_pre_migration"

    const val alwaysShowChapterTransition = "always_show_chapter_transition"

    const val searchPinnedSourcesOnly = "search_pinned_sources_only"

    fun trackUsername(syncId: Int) = "pref_mangasync_username_$syncId"

    fun trackPassword(syncId: Int) = "pref_mangasync_password_$syncId"

    fun trackToken(syncId: Int) = "track_token_$syncId"

    const val eh_lock_hash = "lock_hash"

    const val eh_lock_salt = "lock_salt"

    const val eh_lock_length = "lock_length"

    const val eh_lock_finger = "lock_finger"

    const val eh_lock_manually = "eh_lock_manually"

    const val eh_nh_useHighQualityThumbs = "eh_nh_hq_thumbs"

    const val eh_showSyncIntro = "eh_show_sync_intro"

    const val eh_readOnlySync = "eh_sync_read_only"

    const val eh_lenientSync = "eh_lenient_sync"

    const val eh_useOrigImages = "eh_useOrigImages"

    const val eh_ehSettingsProfile = "eh_ehSettingsProfile"

    const val eh_exhSettingsProfile = "eh_exhSettingsProfile"

    const val eh_settingsKey = "eh_settingsKey"

    const val eh_sessionCookie = "eh_sessionCookie"

    const val eh_hathPerksCookie = "eh_hathPerksCookie"

    const val eh_enableExHentai = "enable_exhentai"

    const val eh_ts_aspNetCookie = "eh_ts_aspNetCookie"

    const val eh_showSettingsUploadWarning = "eh_showSettingsUploadWarning2"

    const val eh_expandFilters = "eh_expand_filters"

    const val eh_readerThreads = "eh_reader_threads"

    const val eh_readerInstantRetry = "eh_reader_instant_retry"

    const val eh_utilAutoscrollInterval = "eh_util_autoscroll_interval"

    const val eh_cacheSize = "eh_cache_size"

    const val eh_preserveReadingPosition = "eh_preserve_reading_position"

    const val eh_autoSolveCaptchas = "eh_autosolve_captchas"

    const val eh_delegateSources = "eh_delegate_sources"

    const val eh_logLevel = "eh_log_level"

    const val eh_enableSourceBlacklist = "eh_enable_source_blacklist"

    const val eh_autoUpdateFrequency = "eh_auto_update_frequency"

    const val eh_autoUpdateRestrictions = "eh_auto_update_restrictions"

    const val eh_autoUpdateStats = "eh_auto_update_stats"

    const val eh_aggressivePageLoading = "eh_aggressive_page_loading"

    const val eh_hl_useHighQualityThumbs = "eh_hl_hq_thumbs"

    const val eh_preload_size = "eh_preload_size"

    const val eh_tag_filtering_value = "eh_tag_filtering_value"

    const val eh_tag_watching_value = "eh_tag_watching_value"

    const val eh_is_hentai_enabled = "eh_is_hentai_enabled"
}
