package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesController
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class PreferencesHelper(val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val flowPrefs = FlowSharedPreferences(prefs)

    private val defaultDownloadsDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "downloads"
    ).toUri()

    private val defaultBackupDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "backup"
    ).toUri()

    fun startScreen() = prefs.getInt(Keys.startScreen, 1)

    fun confirmExit() = prefs.getBoolean(Keys.confirmExit, false)

    fun hideBottomBarOnScroll() = flowPrefs.getBoolean("pref_hide_bottom_bar_on_scroll", true)

    fun sideNavIconAlignment() = flowPrefs.getInt("pref_side_nav_icon_alignment", 0)

    fun useAuthenticator() = flowPrefs.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = flowPrefs.getInt("lock_app_after", 0)

    fun lastAppUnlock() = flowPrefs.getLong("last_app_unlock", 0)

    fun secureScreen() = flowPrefs.getEnum("secure_screen_v2", Values.SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun autoUpdateMetadata() = prefs.getBoolean(Keys.autoUpdateMetadata, false)

    fun autoUpdateTrackers() = prefs.getBoolean(Keys.autoUpdateTrackers, false)

    fun themeMode() = flowPrefs.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.light }
    )

    fun appTheme() = flowPrefs.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT }
    )

    fun themeDarkAmoled() = flowPrefs.getBoolean("pref_theme_dark_amoled_key", false)

    fun pageTransitions() = flowPrefs.getBoolean("pref_enable_transitions_key", true)

    fun doubleTapAnimSpeed() = flowPrefs.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = flowPrefs.getBoolean("pref_show_page_number_key", true)

    fun dualPageSplitPaged() = flowPrefs.getBoolean("pref_dual_page_split", false)

    fun dualPageSplitWebtoon() = flowPrefs.getBoolean("pref_dual_page_split_webtoon", false)

    fun dualPageInvertPaged() = flowPrefs.getBoolean("pref_dual_page_invert", false)

    fun dualPageInvertWebtoon() = flowPrefs.getBoolean("pref_dual_page_invert_webtoon", false)

    fun showReadingMode() = prefs.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = flowPrefs.getBoolean("pref_true_color_key", false)

    fun fullscreen() = flowPrefs.getBoolean("fullscreen", true)

    fun cutoutShort() = flowPrefs.getBoolean("cutout_short", true)

    fun keepScreenOn() = flowPrefs.getBoolean("pref_keep_screen_on_key", true)

    fun customBrightness() = flowPrefs.getBoolean("pref_custom_brightness_key", false)

    fun customBrightnessValue() = flowPrefs.getInt("custom_brightness_value", 0)

    fun colorFilter() = flowPrefs.getBoolean("pref_color_filter_key", false)

    fun colorFilterValue() = flowPrefs.getInt("color_filter_value", 0)

    fun colorFilterMode() = flowPrefs.getInt("color_filter_mode", 0)

    fun grayscale() = flowPrefs.getBoolean("pref_grayscale", false)

    fun invertedColors() = flowPrefs.getBoolean("pref_inverted_colors", false)

    fun defaultReadingMode() = prefs.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = prefs.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = flowPrefs.getInt("pref_image_scale_type_key", 1)

    fun zoomStart() = flowPrefs.getInt("pref_zoom_start_key", 1)

    fun readerTheme() = flowPrefs.getInt("pref_reader_theme_key", 1)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean("always_show_chapter_transition", true)

    fun cropBorders() = flowPrefs.getBoolean("crop_borders", false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean("crop_borders_webtoon", false)

    fun webtoonSidePadding() = flowPrefs.getInt("webtoon_side_padding", 0)

    fun readWithTapping() = flowPrefs.getBoolean("reader_tap", true)

    fun pagerNavInverted() = flowPrefs.getEnum("reader_tapping_inverted", Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = flowPrefs.getEnum("reader_tapping_inverted_webtoon", Values.TappingInvertMode.NONE)

    fun readWithLongTap() = flowPrefs.getBoolean("reader_long_tap", true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean("reader_volume_keys", false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean("reader_volume_keys_inverted", false)

    fun navigationModePager() = flowPrefs.getInt("reader_navigation_mode_pager", 0)

    fun navigationModeWebtoon() = flowPrefs.getInt("reader_navigation_mode_webtoon", 0)

    fun showNavigationOverlayNewUser() = flowPrefs.getBoolean("reader_navigation_overlay_new_user", true)

    fun showNavigationOverlayOnStart() = flowPrefs.getBoolean("reader_navigation_overlay_on_start", false)

    fun readerHideThreshold() = flowPrefs.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun portraitColumns() = flowPrefs.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = flowPrefs.getInt("pref_library_columns_landscape_key", 0)

    fun jumpToChapters() = prefs.getBoolean(Keys.jumpToChapters, false)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedSource() = flowPrefs.getLong("last_catalogue_source", -1)

    fun lastUsedCategory() = flowPrefs.getInt("last_used_category", 0)

    fun lastVersionCode() = flowPrefs.getInt("last_version_code", 0)

    fun sourceDisplayMode() = flowPrefs.getEnum("pref_display_mode_catalogue", DisplayModeSetting.COMPACT_GRID)

    fun enabledLanguages() = flowPrefs.getStringSet("source_languages", setOf("all", "en", Locale.getDefault().language))

    fun trackUsername(sync: TrackService) = prefs.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = prefs.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        prefs.edit {
            putString(Keys.trackUsername(sync.id), username)
            putString(Keys.trackPassword(sync.id), password)
        }
    }

    fun trackToken(sync: TrackService) = flowPrefs.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = flowPrefs.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = flowPrefs.getString("backup_directory", defaultBackupDir.toString())

    fun relativeTime() = flowPrefs.getInt("relative_time", 7)

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = flowPrefs.getString("download_directory", defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun saveChaptersAsCBZ() = flowPrefs.getBoolean("save_chapter_as_cbz", false)

    fun folderPerManga() = prefs.getBoolean(Keys.folderPerManga, false)

    fun numberOfBackups() = flowPrefs.getInt("backup_slots", 1)

    fun backupInterval() = flowPrefs.getInt("backup_interval", 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = prefs.getBoolean(Keys.removeBookmarkedChapters, false)

    fun removeExcludeCategories() = flowPrefs.getStringSet("remove_exclude_categories", emptySet())

    fun libraryUpdateInterval() = flowPrefs.getInt("pref_library_update_interval_key", 24)

    fun libraryUpdateDeviceRestriction() = flowPrefs.getStringSet("library_update_restriction", setOf(DEVICE_ONLY_ON_WIFI))
    fun libraryUpdateMangaRestriction() = flowPrefs.getStringSet("library_update_manga_restriction", setOf(MANGA_FULLY_READ, MANGA_ONGOING))

    fun showUpdatesNavBadge() = flowPrefs.getBoolean("library_update_show_tab_badge", false)
    fun unreadUpdatesCount() = flowPrefs.getInt("library_unread_updates_count", 0)

    fun libraryUpdateCategories() = flowPrefs.getStringSet("library_update_categories", emptySet())
    fun libraryUpdateCategoriesExclude() = flowPrefs.getStringSet("library_update_categories_exclude", emptySet())

    fun libraryDisplayMode() = flowPrefs.getEnum("pref_display_mode_library", DisplayModeSetting.COMPACT_GRID)

    fun downloadBadge() = flowPrefs.getBoolean("display_download_badge", false)

    fun localBadge() = flowPrefs.getBoolean("display_local_badge", true)

    fun downloadedOnly() = flowPrefs.getBoolean("pref_downloaded_only", false)

    fun unreadBadge() = flowPrefs.getBoolean("display_unread_badge", true)

    fun languageBadge() = flowPrefs.getBoolean("display_language_badge", false)

    fun categoryTabs() = flowPrefs.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = flowPrefs.getBoolean("display_number_of_items", false)

    fun filterDownloaded() = flowPrefs.getInt(Keys.filterDownloaded, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterUnread() = flowPrefs.getInt(Keys.filterUnread, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterCompleted() = flowPrefs.getInt(Keys.filterCompleted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterTracking(name: Int) = flowPrefs.getInt("${Keys.filterTracked}_$name", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun librarySortingMode() = flowPrefs.getEnum(Keys.librarySortingMode, SortModeSetting.ALPHABETICAL)
    fun librarySortingAscending() = flowPrefs.getEnum(Keys.librarySortingDirection, SortDirectionSetting.ASCENDING)

    fun migrationSortingMode() = flowPrefs.getEnum(Keys.migrationSortingMode, MigrationSourcesController.SortSetting.ALPHABETICAL)
    fun migrationSortingDirection() = flowPrefs.getEnum(Keys.migrationSortingDirection, MigrationSourcesController.DirectionSetting.ASCENDING)

    fun automaticExtUpdates() = flowPrefs.getBoolean("automatic_ext_updates", true)

    fun showNsfwSource() = flowPrefs.getBoolean("show_nsfw_source", true)

    fun extensionUpdatesCount() = flowPrefs.getInt("ext_updates_count", 0)

    fun lastAppCheck() = flowPrefs.getLong("last_app_check", 0)
    fun lastExtCheck() = flowPrefs.getLong("last_ext_check", 0)

    fun searchPinnedSourcesOnly() = prefs.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun disabledSources() = flowPrefs.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = flowPrefs.getStringSet("pinned_catalogues", emptySet())

    fun downloadNew() = flowPrefs.getBoolean("download_new", false)

    fun downloadNewCategories() = flowPrefs.getStringSet("download_new_categories", emptySet())
    fun downloadNewCategoriesExclude() = flowPrefs.getStringSet("download_new_categories_exclude", emptySet())

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)

    fun categorizedDisplaySettings() = flowPrefs.getBoolean("categorized_display", false)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = flowPrefs.getInt("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = flowPrefs.getStringSet("trusted_signatures", emptySet())

    fun dohProvider() = prefs.getInt(Keys.dohProvider, -1)

    fun lastSearchQuerySearchSettings() = flowPrefs.getString("last_search_query", "")

    fun filterChapterByRead() = prefs.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = prefs.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = prefs.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun sortChapterBySourceOrNumber() = prefs.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.CHAPTER_SORTING_SOURCE)

    fun displayChapterByNameOrNumber() = prefs.getInt(Keys.defaultChapterDisplayByNameOrNumber, Manga.CHAPTER_DISPLAY_NAME)

    fun sortChapterByAscendingOrDescending() = prefs.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.CHAPTER_SORT_DESC)

    fun incognitoMode() = flowPrefs.getBoolean("incognito_mode", false)

    fun tabletUiMode() = flowPrefs.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = flowPrefs.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER
    )

    fun verboseLogging() = prefs.getBoolean(Keys.verboseLogging, false)

    fun autoClearChapterCache() = prefs.getBoolean(Keys.autoClearChapterCache, false)

    fun setChapterSettingsDefault(manga: Manga) {
        prefs.edit {
            putInt(Keys.defaultChapterFilterByRead, manga.readFilter)
            putInt(Keys.defaultChapterFilterByDownloaded, manga.downloadedFilter)
            putInt(Keys.defaultChapterFilterByBookmarked, manga.bookmarkedFilter)
            putInt(Keys.defaultChapterSortBySourceOrNumber, manga.sorting)
            putInt(Keys.defaultChapterDisplayByNameOrNumber, manga.displayMode)
            putInt(Keys.defaultChapterSortByAscendingOrDescending, if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        }
    }
}
