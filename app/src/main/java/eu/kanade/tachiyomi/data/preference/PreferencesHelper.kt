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

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

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
