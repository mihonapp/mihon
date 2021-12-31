package eu.kanade.tachiyomi

import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.MANGA_ONGOING
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper): Boolean {
        val context = preferences.context

        val oldVersion = preferences.lastVersionCode().get()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            preferences.lastVersionCode().set(BuildConfig.VERSION_CODE)

            // Always set up background tasks to ensure they're running
            if (BuildConfig.INCLUDE_UPDATER) {
                AppUpdateJob.setupTask(context)
            }
            ExtensionUpdateJob.setupTask(context)
            LibraryUpdateJob.setupTask(context)
            BackupCreatorJob.setupTask(context)

            // Fresh install
            if (oldVersion == 0) {
                return false
            }

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            if (oldVersion < 14) {
                // Restore jobs after upgrading to Evernote's job scheduler.
                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
                LibraryUpdateJob.setupTask(context)
            }
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
            if (oldVersion < 43) {
                // Restore jobs after migrating from Evernote's job scheduler to WorkManager.
                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
                LibraryUpdateJob.setupTask(context)
                BackupCreatorJob.setupTask(context)

                // New extension update check job
                ExtensionUpdateJob.setupTask(context)
            }
            if (oldVersion < 44) {
                // Reset sorting preference if using removed sort by source
                val oldSortingMode = prefs.getInt(PreferenceKeys.librarySortingMode, 0)

                @Suppress("DEPRECATION")
                if (oldSortingMode == LibrarySort.SOURCE) {
                    prefs.edit {
                        putInt(PreferenceKeys.librarySortingMode, LibrarySort.ALPHA)
                    }
                }
            }
            if (oldVersion < 52) {
                // Migrate library filters to tri-state versions
                fun convertBooleanPrefToTriState(key: String): Int {
                    val oldPrefValue = prefs.getBoolean(key, false)
                    return if (oldPrefValue) ExtendedNavigationView.Item.TriStateGroup.State.INCLUDE.value
                    else ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value
                }
                prefs.edit {
                    putInt(PreferenceKeys.filterDownloaded, convertBooleanPrefToTriState("pref_filter_downloaded_key"))
                    remove("pref_filter_downloaded_key")

                    putInt(PreferenceKeys.filterUnread, convertBooleanPrefToTriState("pref_filter_unread_key"))
                    remove("pref_filter_unread_key")

                    putInt(PreferenceKeys.filterCompleted, convertBooleanPrefToTriState("pref_filter_completed_key"))
                    remove("pref_filter_completed_key")
                }
            }
            if (oldVersion < 54) {
                // Force MAL log out due to login flow change
                // v52: switched from scraping to WebView
                // v53: switched from WebView to OAuth
                val trackManager = Injekt.get<TrackManager>()
                if (trackManager.myAnimeList.isLogged) {
                    trackManager.myAnimeList.logout()
                    context.toast(R.string.myanimelist_relogin)
                }
            }
            if (oldVersion < 57) {
                // Migrate DNS over HTTPS setting
                val wasDohEnabled = prefs.getBoolean("enable_doh", false)
                if (wasDohEnabled) {
                    prefs.edit {
                        putInt(PreferenceKeys.dohProvider, PREF_DOH_CLOUDFLARE)
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

                // Disable update check for Android 5.x users
                if (BuildConfig.INCLUDE_UPDATER && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                    AppUpdateJob.cancelTask(context)
                }
            }
            if (oldVersion < 60) {
                // Re-enable update check that was prevously accidentally disabled for M
                if (BuildConfig.INCLUDE_UPDATER && Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                    AppUpdateJob.setupTask(context)
                }

                // Migrate Rotation and Viewer values to default values for viewer_flags
                val newOrientation = when (prefs.getInt("pref_rotation_type_key", 1)) {
                    1 -> OrientationType.FREE.flagValue
                    2 -> OrientationType.PORTRAIT.flagValue
                    3 -> OrientationType.LANDSCAPE.flagValue
                    4 -> OrientationType.LOCKED_PORTRAIT.flagValue
                    5 -> OrientationType.LOCKED_LANDSCAPE.flagValue
                    else -> OrientationType.FREE.flagValue
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
                val updateInterval = preferences.libraryUpdateInterval().get()
                if (updateInterval == 1 || updateInterval == 2) {
                    preferences.libraryUpdateInterval().set(3)
                    LibraryUpdateJob.setupTask(context, 3)
                }
            }
            if (oldVersion < 64) {
                val oldSortingMode = prefs.getInt(PreferenceKeys.librarySortingMode, 0)
                val oldSortingDirection = prefs.getBoolean(PreferenceKeys.librarySortingDirection, true)

                @Suppress("DEPRECATION")
                val newSortingMode = when (oldSortingMode) {
                    LibrarySort.ALPHA -> SortModeSetting.ALPHABETICAL
                    LibrarySort.LAST_READ -> SortModeSetting.LAST_READ
                    LibrarySort.LAST_CHECKED -> SortModeSetting.LAST_CHECKED
                    LibrarySort.UNREAD -> SortModeSetting.UNREAD
                    LibrarySort.TOTAL -> SortModeSetting.TOTAL_CHAPTERS
                    LibrarySort.LATEST_CHAPTER -> SortModeSetting.LATEST_CHAPTER
                    LibrarySort.CHAPTER_FETCH_DATE -> SortModeSetting.DATE_FETCHED
                    LibrarySort.DATE_ADDED -> SortModeSetting.DATE_ADDED
                    else -> SortModeSetting.ALPHABETICAL
                }

                val newSortingDirection = when (oldSortingDirection) {
                    true -> SortDirectionSetting.ASCENDING
                    else -> SortDirectionSetting.DESCENDING
                }

                prefs.edit(commit = true) {
                    remove(PreferenceKeys.librarySortingMode)
                    remove(PreferenceKeys.librarySortingDirection)
                }

                prefs.edit {
                    putString(PreferenceKeys.librarySortingMode, newSortingMode.name)
                    putString(PreferenceKeys.librarySortingDirection, newSortingDirection.name)
                }
            }
            if (oldVersion < 70) {
                if (preferences.enabledLanguages().isSet()) {
                    preferences.enabledLanguages() += "all"
                }
            }
            if (oldVersion < 71) {
                // Handle removed every 3, 4, 6, and 8 hour library updates
                val updateInterval = preferences.libraryUpdateInterval().get()
                if (updateInterval in listOf(3, 4, 6, 8)) {
                    preferences.libraryUpdateInterval().set(12)
                    LibraryUpdateJob.setupTask(context, 12)
                }
            }
            if (oldVersion < 72) {
                val oldUpdateOngoingOnly = prefs.getBoolean("pref_update_only_non_completed_key", true)
                if (!oldUpdateOngoingOnly) {
                    preferences.libraryUpdateMangaRestriction() -= MANGA_ONGOING
                }
            }

            return true
        }

        return false
    }
}
