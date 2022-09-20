package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
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

    fun confirmExit() = preferenceStore.getBoolean("pref_confirm_exit", false)

    fun sideNavIconAlignment() = preferenceStore.getInt("pref_side_nav_icon_alignment", 0)

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun autoUpdateTrackers() = preferenceStore.getBoolean("auto_update_trackers", false)

    fun themeMode() = preferenceStore.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.light },
    )

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun pageTransitions() = preferenceStore.getBoolean("pref_enable_transitions_key", true)

    fun doubleTapAnimSpeed() = preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    fun showPageNumber() = preferenceStore.getBoolean("pref_show_page_number_key", true)

    fun dualPageSplitPaged() = preferenceStore.getBoolean("pref_dual_page_split", false)

    fun dualPageInvertPaged() = preferenceStore.getBoolean("pref_dual_page_invert", false)

    fun dualPageSplitWebtoon() = preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    fun dualPageInvertWebtoon() = preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    fun longStripSplitWebtoon() = preferenceStore.getBoolean("pref_long_strip_split_webtoon", true)

    fun showReadingMode() = preferenceStore.getBoolean("pref_show_reading_mode", true)

    fun trueColor() = preferenceStore.getBoolean("pref_true_color_key", false)

    fun fullscreen() = preferenceStore.getBoolean("fullscreen", true)

    fun cutoutShort() = preferenceStore.getBoolean("cutout_short", true)

    fun keepScreenOn() = preferenceStore.getBoolean("pref_keep_screen_on_key", true)

    fun customBrightness() = preferenceStore.getBoolean("pref_custom_brightness_key", false)

    fun customBrightnessValue() = preferenceStore.getInt("custom_brightness_value", 0)

    fun colorFilter() = preferenceStore.getBoolean("pref_color_filter_key", false)

    fun colorFilterValue() = preferenceStore.getInt("color_filter_value", 0)

    fun colorFilterMode() = preferenceStore.getInt("color_filter_mode", 0)

    fun grayscale() = preferenceStore.getBoolean("pref_grayscale", false)

    fun invertedColors() = preferenceStore.getBoolean("pref_inverted_colors", false)

    fun defaultReadingMode() = preferenceStore.getInt("pref_default_reading_mode_key", ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = preferenceStore.getInt("pref_default_orientation_type_key", OrientationType.FREE.flagValue)

    fun imageScaleType() = preferenceStore.getInt("pref_image_scale_type_key", 1)

    fun zoomStart() = preferenceStore.getInt("pref_zoom_start_key", 1)

    fun readerTheme() = preferenceStore.getInt("pref_reader_theme_key", 1)

    fun alwaysShowChapterTransition() = preferenceStore.getBoolean("always_show_chapter_transition", true)

    fun cropBorders() = preferenceStore.getBoolean("crop_borders", false)

    fun navigateToPan() = preferenceStore.getBoolean("navigate_pan", true)

    fun landscapeZoom() = preferenceStore.getBoolean("landscape_zoom", true)

    fun cropBordersWebtoon() = preferenceStore.getBoolean("crop_borders_webtoon", false)

    fun webtoonSidePadding() = preferenceStore.getInt("webtoon_side_padding", 0)

    fun pagerNavInverted() = preferenceStore.getEnum("reader_tapping_inverted", Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = preferenceStore.getEnum("reader_tapping_inverted_webtoon", Values.TappingInvertMode.NONE)

    fun readWithLongTap() = preferenceStore.getBoolean("reader_long_tap", true)

    fun readWithVolumeKeys() = preferenceStore.getBoolean("reader_volume_keys", false)

    fun readWithVolumeKeysInverted() = preferenceStore.getBoolean("reader_volume_keys_inverted", false)

    fun navigationModePager() = preferenceStore.getInt("reader_navigation_mode_pager", 0)

    fun navigationModeWebtoon() = preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    fun showNavigationOverlayNewUser() = preferenceStore.getBoolean("reader_navigation_overlay_new_user", true)

    fun showNavigationOverlayOnStart() = preferenceStore.getBoolean("reader_navigation_overlay_on_start", false)

    fun readerHideThreshold() = preferenceStore.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun lastVersionCode() = preferenceStore.getInt("last_version_code", 0)

    fun trackUsername(sync: TrackService) = preferenceStore.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = preferenceStore.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: TrackService) = preferenceStore.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = preferenceStore.getString("backup_directory", defaultBackupDir.toString())

    fun relativeTime() = preferenceStore.getInt("relative_time", 7)

    fun dateFormat(format: String = preferenceStore.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = preferenceStore.getString("download_directory", defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean("pref_download_only_over_wifi_key", true)

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", false)

    fun folderPerManga() = preferenceStore.getBoolean("create_folder_per_manga", false)

    fun numberOfBackups() = preferenceStore.getInt("backup_slots", 2)

    fun backupInterval() = preferenceStore.getInt("backup_interval", 12)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean("pref_remove_after_marked_as_read_key", false)

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun downloadedOnly() = preferenceStore.getBoolean("pref_downloaded_only", false)

    fun automaticExtUpdates() = preferenceStore.getBoolean("automatic_ext_updates", true)

    fun lastAppCheck() = preferenceStore.getLong("last_app_check", 0)
    fun lastExtCheck() = preferenceStore.getLong("last_ext_check", 0)

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet("download_new_categories", emptySet())
    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun skipRead() = preferenceStore.getBoolean("skip_read", false)

    fun skipFiltered() = preferenceStore.getBoolean("skip_filtered", true)

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun filterChapterByRead() = preferenceStore.getInt("default_chapter_filter_by_read", DomainManga.SHOW_ALL.toInt())

    fun filterChapterByDownloaded() = preferenceStore.getInt("default_chapter_filter_by_downloaded", DomainManga.SHOW_ALL.toInt())

    fun filterChapterByBookmarked() = preferenceStore.getInt("default_chapter_filter_by_bookmarked", DomainManga.SHOW_ALL.toInt())

    // and upload date
    fun sortChapterBySourceOrNumber() = preferenceStore.getInt("default_chapter_sort_by_source_or_number", DomainManga.CHAPTER_SORTING_SOURCE.toInt())

    fun displayChapterByNameOrNumber() = preferenceStore.getInt("default_chapter_display_by_name_or_number", DomainManga.CHAPTER_DISPLAY_NAME.toInt())

    fun sortChapterByAscendingOrDescending() = preferenceStore.getInt("default_chapter_sort_by_ascending_or_descending", DomainManga.CHAPTER_SORT_DESC.toInt())

    fun incognitoMode() = preferenceStore.getBoolean("incognito_mode", false)

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = preferenceStore.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER,
    )

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.readFilter)
        filterChapterByDownloaded().set(manga.downloadedFilter)
        filterChapterByBookmarked().set(manga.bookmarkedFilter)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(if (manga.sortDescending()) DomainManga.CHAPTER_SORT_DESC.toInt() else DomainManga.CHAPTER_SORT_ASC.toInt())
    }
}
