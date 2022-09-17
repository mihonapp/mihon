package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode
import eu.kanade.tachiyomi.ui.library.setting.LibrarySort
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.domain.manga.model.Manga as DomainManga
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class PreferencesHelper(
    val context: Context,
    private val preferenceStore: PreferenceStore,
) {

    private val defaultDownloadsDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "downloads",
    ).toUri()

    private val defaultBackupDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "backup",
    ).toUri()

    fun confirmExit() = this.preferenceStore.getBoolean(Keys.confirmExit, false)

    fun sideNavIconAlignment() = this.preferenceStore.getInt("pref_side_nav_icon_alignment", 0)

    fun useAuthenticator() = this.preferenceStore.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = this.preferenceStore.getInt("lock_app_after", 0)

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise this pref should be deleted.
     */
    fun lastAppClosed() = this.preferenceStore.getLong("last_app_closed", 0)

    fun secureScreen() = this.preferenceStore.getEnum("secure_screen_v2", Values.SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = this.preferenceStore.getBoolean(Keys.hideNotificationContent, false)

    fun autoUpdateMetadata() = this.preferenceStore.getBoolean(Keys.autoUpdateMetadata, false)

    fun autoUpdateTrackers() = this.preferenceStore.getBoolean(Keys.autoUpdateTrackers, false)

    fun themeMode() = this.preferenceStore.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.light },
    )

    fun appTheme() = this.preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT },
    )

    fun themeDarkAmoled() = this.preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun pageTransitions() = this.preferenceStore.getBoolean("pref_enable_transitions_key", true)

    fun doubleTapAnimSpeed() = this.preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = this.preferenceStore.getBoolean("pref_show_page_number_key", true)

    fun dualPageSplitPaged() = this.preferenceStore.getBoolean("pref_dual_page_split", false)

    fun dualPageInvertPaged() = this.preferenceStore.getBoolean("pref_dual_page_invert", false)

    fun dualPageSplitWebtoon() = this.preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    fun dualPageInvertWebtoon() = this.preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    fun longStripSplitWebtoon() = this.preferenceStore.getBoolean("pref_long_strip_split_webtoon", true)

    fun showReadingMode() = this.preferenceStore.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = this.preferenceStore.getBoolean("pref_true_color_key", false)

    fun fullscreen() = this.preferenceStore.getBoolean("fullscreen", true)

    fun cutoutShort() = this.preferenceStore.getBoolean("cutout_short", true)

    fun keepScreenOn() = this.preferenceStore.getBoolean("pref_keep_screen_on_key", true)

    fun customBrightness() = this.preferenceStore.getBoolean("pref_custom_brightness_key", false)

    fun customBrightnessValue() = this.preferenceStore.getInt("custom_brightness_value", 0)

    fun colorFilter() = this.preferenceStore.getBoolean("pref_color_filter_key", false)

    fun colorFilterValue() = this.preferenceStore.getInt("color_filter_value", 0)

    fun colorFilterMode() = this.preferenceStore.getInt("color_filter_mode", 0)

    fun grayscale() = this.preferenceStore.getBoolean("pref_grayscale", false)

    fun invertedColors() = this.preferenceStore.getBoolean("pref_inverted_colors", false)

    fun defaultReadingMode() = this.preferenceStore.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = this.preferenceStore.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = this.preferenceStore.getInt("pref_image_scale_type_key", 1)

    fun zoomStart() = this.preferenceStore.getInt("pref_zoom_start_key", 1)

    fun readerTheme() = this.preferenceStore.getInt("pref_reader_theme_key", 1)

    fun alwaysShowChapterTransition() = this.preferenceStore.getBoolean("always_show_chapter_transition", true)

    fun cropBorders() = this.preferenceStore.getBoolean("crop_borders", false)

    fun navigateToPan() = this.preferenceStore.getBoolean("navigate_pan", true)

    fun landscapeZoom() = this.preferenceStore.getBoolean("landscape_zoom", true)

    fun cropBordersWebtoon() = this.preferenceStore.getBoolean("crop_borders_webtoon", false)

    fun webtoonSidePadding() = this.preferenceStore.getInt("webtoon_side_padding", 0)

    fun pagerNavInverted() = this.preferenceStore.getEnum("reader_tapping_inverted", Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = this.preferenceStore.getEnum("reader_tapping_inverted_webtoon", Values.TappingInvertMode.NONE)

    fun readWithLongTap() = this.preferenceStore.getBoolean("reader_long_tap", true)

    fun readWithVolumeKeys() = this.preferenceStore.getBoolean("reader_volume_keys", false)

    fun readWithVolumeKeysInverted() = this.preferenceStore.getBoolean("reader_volume_keys_inverted", false)

    fun navigationModePager() = this.preferenceStore.getInt("reader_navigation_mode_pager", 0)

    fun navigationModeWebtoon() = this.preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    fun showNavigationOverlayNewUser() = this.preferenceStore.getBoolean("reader_navigation_overlay_new_user", true)

    fun showNavigationOverlayOnStart() = this.preferenceStore.getBoolean("reader_navigation_overlay_on_start", false)

    fun readerHideThreshold() = this.preferenceStore.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun portraitColumns() = this.preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = this.preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    fun autoUpdateTrack() = this.preferenceStore.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedSource() = this.preferenceStore.getLong("last_catalogue_source", -1)

    fun lastUsedCategory() = this.preferenceStore.getInt("last_used_category", 0)

    fun lastVersionCode() = this.preferenceStore.getInt("last_version_code", 0)

    fun sourceDisplayMode() = this.preferenceStore.getObject("pref_display_mode_catalogue", LibraryDisplayMode.default, LibraryDisplayMode.Serializer::serialize, LibraryDisplayMode.Serializer::deserialize)

    fun enabledLanguages() = this.preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun trackUsername(sync: TrackService) = this.preferenceStore.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = this.preferenceStore.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: TrackService) = this.preferenceStore.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = this.preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = this.preferenceStore.getString("backup_directory", defaultBackupDir.toString())

    fun relativeTime() = this.preferenceStore.getInt("relative_time", 7)

    fun dateFormat(format: String = this.preferenceStore.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = this.preferenceStore.getString("download_directory", defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = this.preferenceStore.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun saveChaptersAsCBZ() = this.preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = this.preferenceStore.getBoolean("split_tall_images", false)

    fun folderPerManga() = this.preferenceStore.getBoolean(Keys.folderPerManga, false)

    fun numberOfBackups() = this.preferenceStore.getInt("backup_slots", 2)

    fun backupInterval() = this.preferenceStore.getInt("backup_interval", 12)

    fun removeAfterReadSlots() = this.preferenceStore.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = this.preferenceStore.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = this.preferenceStore.getBoolean(Keys.removeBookmarkedChapters, false)

    fun removeExcludeCategories() = this.preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun libraryUpdateInterval() = this.preferenceStore.getInt("pref_library_update_interval_key", 24)
    fun libraryUpdateLastTimestamp() = this.preferenceStore.getLong("library_update_last_timestamp", 0L)

    fun libraryUpdateDeviceRestriction() = this.preferenceStore.getStringSet("library_update_restriction", setOf(DEVICE_ONLY_ON_WIFI))
    fun libraryUpdateMangaRestriction() = this.preferenceStore.getStringSet("library_update_manga_restriction", setOf(MANGA_HAS_UNREAD, MANGA_NON_COMPLETED, MANGA_NON_READ))

    fun showUpdatesNavBadge() = this.preferenceStore.getBoolean("library_update_show_tab_badge", false)
    fun unreadUpdatesCount() = this.preferenceStore.getInt("library_unread_updates_count", 0)

    fun libraryUpdateCategories() = this.preferenceStore.getStringSet("library_update_categories", emptySet())
    fun libraryUpdateCategoriesExclude() = this.preferenceStore.getStringSet("library_update_categories_exclude", emptySet())

    fun libraryDisplayMode() = this.preferenceStore.getObject("pref_display_mode_library", LibraryDisplayMode.default, LibraryDisplayMode.Serializer::serialize, LibraryDisplayMode.Serializer::deserialize)

    fun downloadBadge() = this.preferenceStore.getBoolean("display_download_badge", false)

    fun localBadge() = this.preferenceStore.getBoolean("display_local_badge", true)

    fun downloadedOnly() = this.preferenceStore.getBoolean("pref_downloaded_only", false)

    fun unreadBadge() = this.preferenceStore.getBoolean("display_unread_badge", true)

    fun languageBadge() = this.preferenceStore.getBoolean("display_language_badge", false)

    fun categoryTabs() = this.preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = this.preferenceStore.getBoolean("display_number_of_items", false)

    fun filterDownloaded() = this.preferenceStore.getInt(Keys.filterDownloaded, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterUnread() = this.preferenceStore.getInt(Keys.filterUnread, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterStarted() = this.preferenceStore.getInt(Keys.filterStarted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterCompleted() = this.preferenceStore.getInt(Keys.filterCompleted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterTracking(name: Int) = this.preferenceStore.getInt("${Keys.filterTracked}_$name", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun librarySortingMode() = this.preferenceStore.getObject(Keys.librarySortingMode, LibrarySort.default, LibrarySort.Serializer::serialize, LibrarySort.Serializer::deserialize)

    fun migrationSortingMode() = this.preferenceStore.getEnum(Keys.migrationSortingMode, SetMigrateSorting.Mode.ALPHABETICAL)
    fun migrationSortingDirection() = this.preferenceStore.getEnum(Keys.migrationSortingDirection, SetMigrateSorting.Direction.ASCENDING)

    fun automaticExtUpdates() = this.preferenceStore.getBoolean("automatic_ext_updates", true)

    fun showNsfwSource() = this.preferenceStore.getBoolean("show_nsfw_source", true)

    fun extensionUpdatesCount() = this.preferenceStore.getInt("ext_updates_count", 0)

    fun lastAppCheck() = this.preferenceStore.getLong("last_app_check", 0)
    fun lastExtCheck() = this.preferenceStore.getLong("last_ext_check", 0)

    fun searchPinnedSourcesOnly() = this.preferenceStore.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun disabledSources() = this.preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = this.preferenceStore.getStringSet("pinned_catalogues", emptySet())

    fun downloadNewChapters() = this.preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = this.preferenceStore.getStringSet("download_new_categories", emptySet())
    fun downloadNewChapterCategoriesExclude() = this.preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun autoDownloadWhileReading() = this.preferenceStore.getInt("auto_download_while_reading", 0)

    fun defaultCategory() = this.preferenceStore.getInt(Keys.defaultCategory, -1)

    fun categorizedDisplaySettings() = this.preferenceStore.getBoolean("categorized_display", false)

    fun skipRead() = this.preferenceStore.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = this.preferenceStore.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = this.preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = this.preferenceStore.getStringSet("trusted_signatures", emptySet())

    fun filterChapterByRead() = this.preferenceStore.getInt(Keys.defaultChapterFilterByRead, DomainManga.SHOW_ALL.toInt())

    fun filterChapterByDownloaded() = this.preferenceStore.getInt(Keys.defaultChapterFilterByDownloaded, DomainManga.SHOW_ALL.toInt())

    fun filterChapterByBookmarked() = this.preferenceStore.getInt(Keys.defaultChapterFilterByBookmarked, DomainManga.SHOW_ALL.toInt())

    fun sortChapterBySourceOrNumber() = this.preferenceStore.getInt(Keys.defaultChapterSortBySourceOrNumber, DomainManga.CHAPTER_SORTING_SOURCE.toInt())

    fun displayChapterByNameOrNumber() = this.preferenceStore.getInt(Keys.defaultChapterDisplayByNameOrNumber, DomainManga.CHAPTER_DISPLAY_NAME.toInt())

    fun sortChapterByAscendingOrDescending() = this.preferenceStore.getInt(Keys.defaultChapterSortByAscendingOrDescending, DomainManga.CHAPTER_SORT_DESC.toInt())

    fun incognitoMode() = this.preferenceStore.getBoolean("incognito_mode", false)

    fun tabletUiMode() = this.preferenceStore.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = this.preferenceStore.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER,
    )

    fun autoClearChapterCache() = this.preferenceStore.getBoolean(Keys.autoClearChapterCache, false)

    fun duplicatePinnedSources() = this.preferenceStore.getBoolean("duplicate_pinned_sources", false)

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.readFilter)
        filterChapterByDownloaded().set(manga.downloadedFilter)
        filterChapterByBookmarked().set(manga.bookmarkedFilter)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(if (manga.sortDescending()) DomainManga.CHAPTER_SORT_DESC.toInt() else DomainManga.CHAPTER_SORT_ASC.toInt())
    }
}
