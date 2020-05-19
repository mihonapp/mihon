package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.preference.PreferenceManager
import com.f2prateek.rx.preferences.Preference as RxPreference
import com.f2prateek.rx.preferences.RxSharedPreferences
import com.tfcporciuncula.flow.FlowSharedPreferences
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

fun <T> RxPreference<T>.getOrDefault(): T = get() ?: defaultValue()!!

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Preference<T>.asImmediateFlow(block: (value: T) -> Unit): Flow<T> {
    block(get())
    return asFlow()
        .onEach { block(it) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesHelper(val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val rxPrefs = RxSharedPreferences.create(prefs)
    val flowPrefs = FlowSharedPreferences(prefs)

    private val defaultDownloadsDir = Uri.fromFile(
        File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.getString(R.string.app_name),
            "downloads"
        )
    )

    private val defaultBackupDir = Uri.fromFile(
        File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.getString(R.string.app_name),
            "backup"
        )
    )

    fun startScreen() = prefs.getInt(Keys.startScreen, 1)

    fun confirmExit() = prefs.getBoolean(Keys.confirmExit, false)

    fun useBiometricLock() = flowPrefs.getBoolean(Keys.useBiometricLock, false)

    fun lockAppAfter() = flowPrefs.getInt(Keys.lockAppAfter, 0)

    fun lastAppUnlock() = flowPrefs.getLong(Keys.lastAppUnlock, 0)

    fun secureScreen() = flowPrefs.getBoolean(Keys.secureScreen, false)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun clear() = prefs.edit().clear().apply()

    fun themeMode() = flowPrefs.getString(Keys.themeMode, Values.THEME_MODE_SYSTEM)

    fun themeLight() = flowPrefs.getString(Keys.themeLight, Values.THEME_LIGHT_DEFAULT)

    fun themeDark() = flowPrefs.getString(Keys.themeDark, Values.THEME_DARK_DEFAULT)

    fun rotation() = flowPrefs.getInt(Keys.rotation, 1)

    fun pageTransitions() = flowPrefs.getBoolean(Keys.enableTransitions, true)

    fun doubleTapAnimSpeed() = flowPrefs.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = flowPrefs.getBoolean(Keys.showPageNumber, true)

    fun trueColor() = flowPrefs.getBoolean(Keys.trueColor, false)

    fun fullscreen() = flowPrefs.getBoolean(Keys.fullscreen, true)

    fun cutoutShort() = flowPrefs.getBoolean(Keys.cutoutShort, true)

    fun keepScreenOn() = flowPrefs.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = flowPrefs.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = flowPrefs.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = flowPrefs.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = flowPrefs.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = flowPrefs.getInt(Keys.colorFilterMode, 0)

    fun defaultViewer() = prefs.getInt(Keys.defaultViewer, 1)

    fun imageScaleType() = flowPrefs.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = flowPrefs.getInt(Keys.zoomStart, 1)

    fun readerTheme() = flowPrefs.getInt(Keys.readerTheme, 1)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun cropBorders() = flowPrefs.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean(Keys.cropBordersWebtoon, false)

    fun webtoonSidePadding() = flowPrefs.getInt(Keys.webtoonSidePadding, 0)

    fun readWithTapping() = flowPrefs.getBoolean(Keys.readWithTapping, true)

    fun readWithLongTap() = flowPrefs.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun portraitColumns() = rxPrefs.getInteger(Keys.portraitColumns, 0)

    fun landscapeColumns() = rxPrefs.getInteger(Keys.landscapeColumns, 0)

    fun updateOnlyNonCompleted() = prefs.getBoolean(Keys.updateOnlyNonCompleted, false)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedCatalogueSource() = rxPrefs.getLong(Keys.lastUsedCatalogueSource, -1)

    fun lastUsedCategory() = flowPrefs.getInt(Keys.lastUsedCategory, 0)

    fun lastVersionCode() = flowPrefs.getInt("last_version_code", 0)

    fun catalogueAsList() = rxPrefs.getBoolean(Keys.catalogueAsList, false)

    fun enabledLanguages() = flowPrefs.getStringSet(Keys.enabledLanguages, setOf("all", "en", Locale.getDefault().language))

    fun sourceSorting() = flowPrefs.getInt(Keys.sourcesSort, 0)

    fun trackUsername(sync: TrackService) = prefs.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = prefs.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        prefs.edit()
            .putString(Keys.trackUsername(sync.id), username)
            .putString(Keys.trackPassword(sync.id), password)
            .apply()
    }

    fun trackToken(sync: TrackService) = flowPrefs.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = flowPrefs.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = flowPrefs.getString(Keys.backupDirectory, defaultBackupDir.toString())

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = flowPrefs.getString(Keys.downloadsDirectory, defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun numberOfBackups() = flowPrefs.getInt(Keys.numberOfBackups, 1)

    fun backupInterval() = flowPrefs.getInt(Keys.backupInterval, 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun libraryUpdateInterval() = flowPrefs.getInt(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateRestriction() = prefs.getStringSet(Keys.libraryUpdateRestriction, setOf("wifi"))

    fun libraryUpdateCategories() = flowPrefs.getStringSet(Keys.libraryUpdateCategories, emptySet())

    fun libraryUpdatePrioritization() = flowPrefs.getInt(Keys.libraryUpdatePrioritization, 0)

    fun libraryAsList() = flowPrefs.getBoolean(Keys.libraryAsList, false)

    fun downloadBadge() = flowPrefs.getBoolean(Keys.downloadBadge, false)

    fun downloadedOnly() = flowPrefs.getBoolean(Keys.downloadedOnly, false)

    fun unreadBadge() = flowPrefs.getBoolean(Keys.unreadBadge, true)

    // J2K converted from boolean to integer
    fun filterDownloaded() = flowPrefs.getInt(Keys.filterDownloaded, 0)

    fun filterUnread() = flowPrefs.getInt(Keys.filterUnread, 0)

    fun filterCompleted() = flowPrefs.getInt(Keys.filterCompleted, 0)

    fun filterTracked() = flowPrefs.getInt(Keys.filterTracked, 0)

    fun filterLewd() = flowPrefs.getInt(Keys.filterLewd, 0)

    fun librarySortingMode() = flowPrefs.getInt(Keys.librarySortingMode, 0)

    fun librarySortingAscending() = flowPrefs.getBoolean("library_sorting_ascending", true)

    fun automaticExtUpdates() = flowPrefs.getBoolean(Keys.automaticExtUpdates, true)

    fun extensionUpdatesCount() = flowPrefs.getInt("ext_updates_count", 0)

    fun lastExtCheck() = flowPrefs.getLong("last_ext_check", 0)

    fun searchPinnedSourcesOnly() = prefs.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun hiddenCatalogues() = flowPrefs.getStringSet("hidden_catalogues", mutableSetOf())

    fun pinnedCatalogues() = flowPrefs.getStringSet("pinned_catalogues", emptySet())

    fun downloadNew() = flowPrefs.getBoolean(Keys.downloadNew, false)

    fun downloadNewCategories() = flowPrefs.getStringSet(Keys.downloadNewCategories, emptySet())

    fun lang() = prefs.getString(Keys.lang, "")

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = flowPrefs.getInt("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = flowPrefs.getStringSet("trusted_signatures", emptySet())

    // --> AZ J2K CHERRYPICKING

    fun defaultMangaOrder() = flowPrefs.getString("default_manga_order", "")

    fun migrationSources() = flowPrefs.getString("migrate_sources", "")

    fun smartMigration() = flowPrefs.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = flowPrefs.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = flowPrefs.getBoolean(Keys.skipPreMigration, false)

    fun upgradeFilters() {
        val filterDl = flowPrefs.getBoolean(Keys.filterDownloaded, false).get()
        val filterUn = flowPrefs.getBoolean(Keys.filterUnread, false).get()
        val filterCm = flowPrefs.getBoolean(Keys.filterCompleted, false).get()
        filterDownloaded().set(if (filterDl) 1 else 0)
        filterUnread().set(if (filterUn) 1 else 0)
        filterCompleted().set(if (filterCm) 1 else 0)
    }

    // <--

    // --> EH
    fun eh_isHentaiEnabled() = flowPrefs.getBoolean(Keys.eh_is_hentai_enabled, true)

    fun enableExhentai() = flowPrefs.getBoolean(Keys.eh_enableExHentai, false)

    fun secureEXH() = flowPrefs.getBoolean("secure_exh", true)

    fun imageQuality() = flowPrefs.getString("ehentai_quality", "auto")

    fun useHentaiAtHome() = flowPrefs.getBoolean("enable_hah", true)

    fun useJapaneseTitle() = flowPrefs.getBoolean("use_jp_title", false)

    fun eh_useOriginalImages() = flowPrefs.getBoolean(Keys.eh_useOrigImages, false)

    fun ehTagFilterValue() = flowPrefs.getInt(Keys.eh_tag_filtering_value, 0)

    fun ehTagWatchingValue() = flowPrefs.getInt(Keys.eh_tag_watching_value, 0)

    fun ehSearchSize() = flowPrefs.getString("ex_search_size", "rc_0")

    fun thumbnailRows() = flowPrefs.getString("ex_thumb_rows", "tr_2")

    fun hasPerformedURLMigration() = flowPrefs.getBoolean("performed_url_migration", false)

    // EH Cookies
    fun memberIdVal() = flowPrefs.getString("eh_ipb_member_id", "")

    fun passHashVal() = flowPrefs.getString("eh_ipb_pass_hash", "")
    fun igneousVal() = flowPrefs.getString("eh_igneous", "")
    fun eh_ehSettingsProfile() = flowPrefs.getInt(Keys.eh_ehSettingsProfile, -1)
    fun eh_exhSettingsProfile() = flowPrefs.getInt(Keys.eh_exhSettingsProfile, -1)
    fun eh_settingsKey() = flowPrefs.getString(Keys.eh_settingsKey, "")
    fun eh_sessionCookie() = flowPrefs.getString(Keys.eh_sessionCookie, "")
    fun eh_hathPerksCookies() = flowPrefs.getString(Keys.eh_hathPerksCookie, "")

    fun eh_nh_useHighQualityThumbs() = flowPrefs.getBoolean(Keys.eh_nh_useHighQualityThumbs, false)

    fun eh_showSyncIntro() = flowPrefs.getBoolean(Keys.eh_showSyncIntro, true)

    fun eh_readOnlySync() = flowPrefs.getBoolean(Keys.eh_readOnlySync, false)

    fun eh_lenientSync() = flowPrefs.getBoolean(Keys.eh_lenientSync, false)

    fun eh_ts_aspNetCookie() = flowPrefs.getString(Keys.eh_ts_aspNetCookie, "")

    fun eh_showSettingsUploadWarning() = flowPrefs.getBoolean(Keys.eh_showSettingsUploadWarning, true)

    fun eh_expandFilters() = flowPrefs.getBoolean(Keys.eh_expandFilters, false)

    fun eh_readerThreads() = flowPrefs.getInt(Keys.eh_readerThreads, 2)

    fun eh_readerInstantRetry() = flowPrefs.getBoolean(Keys.eh_readerInstantRetry, true)

    fun eh_utilAutoscrollInterval() = flowPrefs.getFloat(Keys.eh_utilAutoscrollInterval, 3f)

    fun eh_cacheSize() = flowPrefs.getString(Keys.eh_cacheSize, "75")

    fun eh_preserveReadingPosition() = flowPrefs.getBoolean(Keys.eh_preserveReadingPosition, false)

    fun eh_autoSolveCaptchas() = flowPrefs.getBoolean(Keys.eh_autoSolveCaptchas, false)

    fun eh_delegateSources() = flowPrefs.getBoolean(Keys.eh_delegateSources, true)

    fun eh_lastVersionCode() = flowPrefs.getInt("eh_last_version_code", 0)

    fun eh_savedSearches() = flowPrefs.getStringSet("eh_saved_searches", emptySet())

    fun eh_logLevel() = flowPrefs.getInt(Keys.eh_logLevel, 0)

    fun eh_enableSourceBlacklist() = flowPrefs.getBoolean(Keys.eh_enableSourceBlacklist, true)

    fun eh_autoUpdateFrequency() = flowPrefs.getInt(Keys.eh_autoUpdateFrequency, 1)

    fun eh_autoUpdateRequirements() = prefs.getStringSet(Keys.eh_autoUpdateRestrictions, emptySet())

    fun eh_autoUpdateStats() = flowPrefs.getString(Keys.eh_autoUpdateStats, "")

    fun eh_aggressivePageLoading() = flowPrefs.getBoolean(Keys.eh_aggressivePageLoading, false)

    fun eh_hl_useHighQualityThumbs() = flowPrefs.getBoolean(Keys.eh_hl_useHighQualityThumbs, false)

    fun eh_preload_size() = flowPrefs.getInt(Keys.eh_preload_size, 4)

    fun eh_useNewMangaInterface() = flowPrefs.getBoolean(Keys.eh_use_new_manga_interface, true)
}
