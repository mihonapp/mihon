package eu.kanade.tachiyomi

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.TriState
import tachiyomi.core.preference.getAndSet
import tachiyomi.core.preference.getEnum
import tachiyomi.core.preference.minusAssign
import tachiyomi.core.preference.plusAssign
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.i18n.MR
import java.io.File

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(
        context: Context,
        preferenceStore: PreferenceStore,
        basePreferences: BasePreferences,
        uiPreferences: UiPreferences,
        networkPreferences: NetworkPreferences,
        sourcePreferences: SourcePreferences,
        securityPreferences: SecurityPreferences,
        libraryPreferences: LibraryPreferences,
        readerPreferences: ReaderPreferences,
        backupPreferences: BackupPreferences,
        trackerManager: TrackerManager,
    ): Boolean {
        val lastVersionCode = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        val oldVersion = lastVersionCode.get()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            lastVersionCode.set(BuildConfig.VERSION_CODE)

            // Always set up background tasks to ensure they're running
            LibraryUpdateJob.setupTask(context)
            BackupCreateJob.setupTask(context)

            // Fresh install
            if (oldVersion == 0) {
                return false
            }

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            if (oldVersion < 15) {
                // Delete internal chapter cache dir.
                File(context.cacheDir, "chapter_disk_cache").deleteRecursively()
            }
            if (oldVersion < 19) {
                // Move covers to external files dir.
                val oldDir = File(context.externalCacheDir, "cover_disk_cache")
                if (oldDir.exists()) {
                    val destDir = context.getExternalFilesDir("covers")
                    if (destDir != null) {
                        oldDir.listFiles()?.forEach {
                            it.renameTo(File(destDir, it.name))
                        }
                    }
                }
            }
            if (oldVersion < 26) {
                // Delete external chapter cache dir.
                val extCache = context.externalCacheDir
                if (extCache != null) {
                    val chapterCache = File(extCache, "chapter_disk_cache")
                    if (chapterCache.exists()) {
                        chapterCache.deleteRecursively()
                    }
                }
            }
            if (oldVersion < 44) {
                // Reset sorting preference if using removed sort by source
                val oldSortingMode = prefs.getInt(libraryPreferences.sortingMode().key(), 0)

                if (oldSortingMode == 5) { // SOURCE = 5
                    prefs.edit {
                        putInt(libraryPreferences.sortingMode().key(), 0) // ALPHABETICAL = 0
                    }
                }
            }
            if (oldVersion < 52) {
                // Migrate library filters to tri-state versions
                fun convertBooleanPrefToTriState(key: String): Int {
                    val oldPrefValue = prefs.getBoolean(key, false)
                    return if (oldPrefValue) {
                        1
                    } else {
                        0
                    }
                }
                prefs.edit {
                    putInt(
                        libraryPreferences.filterDownloaded().key(),
                        convertBooleanPrefToTriState("pref_filter_downloaded_key"),
                    )
                    remove("pref_filter_downloaded_key")

                    putInt(
                        libraryPreferences.filterUnread().key(),
                        convertBooleanPrefToTriState("pref_filter_unread_key"),
                    )
                    remove("pref_filter_unread_key")

                    putInt(
                        libraryPreferences.filterCompleted().key(),
                        convertBooleanPrefToTriState("pref_filter_completed_key"),
                    )
                    remove("pref_filter_completed_key")
                }
            }
            if (oldVersion < 54) {
                // Force MAL log out due to login flow change
                // v52: switched from scraping to WebView
                // v53: switched from WebView to OAuth
                if (trackerManager.myAnimeList.isLoggedIn) {
                    trackerManager.myAnimeList.logout()
                    context.toast(MR.strings.myanimelist_relogin)
                }
            }
            if (oldVersion < 57) {
                // Migrate DNS over HTTPS setting
                val wasDohEnabled = prefs.getBoolean("enable_doh", false)
                if (wasDohEnabled) {
                    prefs.edit {
                        putInt(networkPreferences.dohProvider().key(), PREF_DOH_CLOUDFLARE)
                        remove("enable_doh")
                    }
                }
            }
            if (oldVersion < 59) {
                // Reset rotation to Free after replacing Lock
                if (prefs.contains("pref_rotation_type_key")) {
                    prefs.edit {
                        putInt("pref_rotation_type_key", 1)
                    }
                }
            }
            if (oldVersion < 60) {
                // Migrate Rotation and Viewer values to default values for viewer_flags
                val newOrientation = when (prefs.getInt("pref_rotation_type_key", 1)) {
                    1 -> ReaderOrientation.FREE.flagValue
                    2 -> ReaderOrientation.PORTRAIT.flagValue
                    3 -> ReaderOrientation.LANDSCAPE.flagValue
                    4 -> ReaderOrientation.LOCKED_PORTRAIT.flagValue
                    5 -> ReaderOrientation.LOCKED_LANDSCAPE.flagValue
                    else -> ReaderOrientation.FREE.flagValue
                }

                // Reading mode flag and prefValue is the same value
                val newReadingMode = prefs.getInt("pref_default_viewer_key", 1)

                prefs.edit {
                    putInt("pref_default_orientation_type_key", newOrientation)
                    remove("pref_rotation_type_key")
                    putInt("pref_default_reading_mode_key", newReadingMode)
                    remove("pref_default_viewer_key")
                }
            }
            if (oldVersion < 61) {
                // Handle removed every 1 or 2 hour library updates
                val updateInterval = libraryPreferences.autoUpdateInterval().get()
                if (updateInterval == 1 || updateInterval == 2) {
                    libraryPreferences.autoUpdateInterval().set(3)
                    LibraryUpdateJob.setupTask(context, 3)
                }
            }
            if (oldVersion < 64) {
                val oldSortingMode = prefs.getInt(libraryPreferences.sortingMode().key(), 0)
                val oldSortingDirection = prefs.getBoolean("library_sorting_ascending", true)

                val newSortingMode = when (oldSortingMode) {
                    0 -> "ALPHABETICAL"
                    1 -> "LAST_READ"
                    2 -> "LAST_CHECKED"
                    3 -> "UNREAD"
                    4 -> "TOTAL_CHAPTERS"
                    6 -> "LATEST_CHAPTER"
                    8 -> "DATE_FETCHED"
                    7 -> "DATE_ADDED"
                    else -> "ALPHABETICAL"
                }

                val newSortingDirection = when (oldSortingDirection) {
                    true -> "ASCENDING"
                    else -> "DESCENDING"
                }

                prefs.edit(commit = true) {
                    remove(libraryPreferences.sortingMode().key())
                    remove("library_sorting_ascending")
                }

                prefs.edit {
                    putString(libraryPreferences.sortingMode().key(), newSortingMode)
                    putString("library_sorting_ascending", newSortingDirection)
                }
            }
            if (oldVersion < 70) {
                if (sourcePreferences.enabledLanguages().isSet()) {
                    sourcePreferences.enabledLanguages() += "all"
                }
            }
            if (oldVersion < 71) {
                // Handle removed every 3, 4, 6, and 8 hour library updates
                val updateInterval = libraryPreferences.autoUpdateInterval().get()
                if (updateInterval in listOf(3, 4, 6, 8)) {
                    libraryPreferences.autoUpdateInterval().set(12)
                    LibraryUpdateJob.setupTask(context, 12)
                }
            }
            if (oldVersion < 72) {
                val oldUpdateOngoingOnly = prefs.getBoolean("pref_update_only_non_completed_key", true)
                if (!oldUpdateOngoingOnly) {
                    libraryPreferences.autoUpdateMangaRestrictions() -= MANGA_NON_COMPLETED
                }
            }
            if (oldVersion < 75) {
                val oldSecureScreen = prefs.getBoolean("secure_screen", false)
                if (oldSecureScreen) {
                    securityPreferences.secureScreen().set(SecurityPreferences.SecureScreenMode.ALWAYS)
                }
                if (
                    DeviceUtil.isMiui &&
                    basePreferences.extensionInstaller().get() == BasePreferences.ExtensionInstaller.PACKAGEINSTALLER
                ) {
                    basePreferences.extensionInstaller().set(BasePreferences.ExtensionInstaller.LEGACY)
                }
            }
            if (oldVersion < 77) {
                val oldReaderTap = prefs.getBoolean("reader_tap", false)
                if (!oldReaderTap) {
                    readerPreferences.navigationModePager().set(5)
                    readerPreferences.navigationModeWebtoon().set(5)
                }
            }
            if (oldVersion < 81) {
                // Handle renamed enum values
                prefs.edit {
                    val newSortingMode = when (
                        val oldSortingMode = prefs.getString(
                            libraryPreferences.sortingMode().key(),
                            "ALPHABETICAL",
                        )
                    ) {
                        "LAST_CHECKED" -> "LAST_MANGA_UPDATE"
                        "UNREAD" -> "UNREAD_COUNT"
                        "DATE_FETCHED" -> "CHAPTER_FETCH_DATE"
                        else -> oldSortingMode
                    }
                    putString(libraryPreferences.sortingMode().key(), newSortingMode)
                }
            }
            if (oldVersion < 82) {
                prefs.edit {
                    val sort = prefs.getString(libraryPreferences.sortingMode().key(), null) ?: return@edit
                    val direction = prefs.getString("library_sorting_ascending", "ASCENDING")!!
                    putString(libraryPreferences.sortingMode().key(), "$sort,$direction")
                    remove("library_sorting_ascending")
                }
            }
            if (oldVersion < 84) {
                if (backupPreferences.backupInterval().get() == 0) {
                    backupPreferences.backupInterval().set(12)
                    BackupCreateJob.setupTask(context)
                }
            }
            if (oldVersion < 85) {
                val preferences = listOf(
                    libraryPreferences.filterChapterByRead(),
                    libraryPreferences.filterChapterByDownloaded(),
                    libraryPreferences.filterChapterByBookmarked(),
                    libraryPreferences.sortChapterBySourceOrNumber(),
                    libraryPreferences.displayChapterByNameOrNumber(),
                    libraryPreferences.sortChapterByAscendingOrDescending(),
                )

                prefs.edit {
                    preferences.forEach { preference ->
                        val key = preference.key()
                        val value = prefs.getInt(key, Int.MIN_VALUE)
                        if (value == Int.MIN_VALUE) return@forEach
                        remove(key)
                        putLong(key, value.toLong())
                    }
                }
            }
            if (oldVersion < 86) {
                if (uiPreferences.themeMode().isSet()) {
                    prefs.edit {
                        val themeMode = prefs.getString(uiPreferences.themeMode().key(), null) ?: return@edit
                        putString(uiPreferences.themeMode().key(), themeMode.uppercase())
                    }
                }
            }
            if (oldVersion < 92) {
                val trackingQueuePref = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)
                trackingQueuePref.all.forEach {
                    val (_, lastChapterRead) = it.value.toString().split(":")
                    trackingQueuePref.edit {
                        remove(it.key)
                        putFloat(it.key, lastChapterRead.toFloat())
                    }
                }
            }
            if (oldVersion < 96) {
                LibraryUpdateJob.cancelAllWorks(context)
                LibraryUpdateJob.setupTask(context)
            }
            if (oldVersion < 97) {
                // Removed background jobs
                context.workManager.cancelAllWorkByTag("UpdateChecker")
                context.workManager.cancelAllWorkByTag("ExtensionUpdate")
                prefs.edit {
                    remove("automatic_ext_updates")
                }
            }
            if (oldVersion < 99) {
                val prefKeys = listOf(
                    "pref_filter_library_downloaded",
                    "pref_filter_library_unread",
                    "pref_filter_library_started",
                    "pref_filter_library_bookmarked",
                    "pref_filter_library_completed",
                ) + trackerManager.trackers.map { "pref_filter_library_tracked_${it.id}" }

                prefKeys.forEach { key ->
                    val pref = preferenceStore.getInt(key, 0)
                    prefs.edit {
                        remove(key)

                        val newValue = when (pref.get()) {
                            1 -> TriState.ENABLED_IS
                            2 -> TriState.ENABLED_NOT
                            else -> TriState.DISABLED
                        }

                        preferenceStore.getEnum("${key}_v2", TriState.DISABLED).set(newValue)
                    }
                }
            }
            if (oldVersion < 105) {
                val pref = libraryPreferences.autoUpdateDeviceRestrictions()
                if (pref.isSet() && "battery_not_low" in pref.get()) {
                    pref.getAndSet { it - "battery_not_low" }
                }
            }
            if (oldVersion < 106) {
                val pref = preferenceStore.getInt("relative_time", 7)
                if (pref.get() == 0) {
                    uiPreferences.relativeTime().set(false)
                }
            }
            if (oldVersion < 113) {
                val prefsToReplace = listOf(
                    "pref_download_only",
                    "incognito_mode",
                    "last_catalogue_source",
                    "trusted_signatures",
                    "last_app_closed",
                    "library_update_last_timestamp",
                    "library_unseen_updates_count",
                    "last_used_category",
                    "last_app_check",
                    "last_ext_check",
                    "last_version_code",
                    "storage_dir",
                )
                replacePreferences(
                    preferenceStore = preferenceStore,
                    filterPredicate = { it.key in prefsToReplace },
                    newKey = { Preference.appStateKey(it) },
                )

                // Deleting old download cache index files, but might as well clear it all out
                context.cacheDir.deleteRecursively()
            }
            if (oldVersion < 114) {
                sourcePreferences.extensionRepos().getAndSet {
                    it.map { repo -> "https://raw.githubusercontent.com/$repo/repo" }.toSet()
                }
            }
            if (oldVersion < 116) {
                replacePreferences(
                    preferenceStore = preferenceStore,
                    filterPredicate = { it.key.startsWith("pref_mangasync_") || it.key.startsWith("track_token_") },
                    newKey = { Preference.privateKey(it) },
                )
            }
            if (oldVersion < 117) {
                prefs.edit {
                    remove(Preference.appStateKey("trusted_signatures"))
                }
            }
            return true
        }

        return false
    }
}

@Suppress("UNCHECKED_CAST")
private fun replacePreferences(
    preferenceStore: PreferenceStore,
    filterPredicate: (Map.Entry<String, Any?>) -> Boolean,
    newKey: (String) -> String,
) {
    preferenceStore.getAll()
        .filter(filterPredicate)
        .forEach { (key, value) ->
            when (value) {
                is Int -> {
                    preferenceStore.getInt(newKey(key)).set(value)
                    preferenceStore.getInt(key).delete()
                }
                is Long -> {
                    preferenceStore.getLong(newKey(key)).set(value)
                    preferenceStore.getLong(key).delete()
                }
                is Float -> {
                    preferenceStore.getFloat(newKey(key)).set(value)
                    preferenceStore.getFloat(key).delete()
                }
                is String -> {
                    preferenceStore.getString(newKey(key)).set(value)
                    preferenceStore.getString(key).delete()
                }
                is Boolean -> {
                    preferenceStore.getBoolean(newKey(key)).set(value)
                    preferenceStore.getBoolean(key).delete()
                }
                is Set<*> -> (value as? Set<String>)?.let {
                    preferenceStore.getStringSet(newKey(key)).set(value)
                    preferenceStore.getStringSet(key).delete()
                }
            }
        }
}
