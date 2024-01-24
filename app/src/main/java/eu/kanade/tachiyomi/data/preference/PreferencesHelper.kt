package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.recents.RecentMangaAdapter
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.util.system.Themes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

fun <T> Preference<T>.changesIn(scope: CoroutineScope, block: (value: T) -> Unit): Job {
    block(get())
    return changes()
        .onEach { block(it) }
        .launchIn(scope)
}

fun Preference<Boolean>.toggle() = set(!get())

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

operator fun <T> Preference<Set<T>>.plusAssign(item: Collection<T>) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: Collection<T>) {
    set(get() - item)
}

class PreferencesHelper(val context: Context, val preferenceStore: PreferenceStore) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val defaultDownloadsDir = Uri.fromFile(
        File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.getString(R.string.app_name),
            "downloads",
        ),
    )

    private val defaultBackupDir = Uri.fromFile(
        File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.getString(R.string.app_name),
            "backup",
        ),
    )

    fun getInt(key: String, default: Int) = preferenceStore.getInt(key, default)
    fun getStringPref(key: String, default: String = "") = preferenceStore.getString(key, default)
    fun getStringSet(key: String, default: Set<String>) = preferenceStore.getStringSet(key, default)

    fun startingTab() = preferenceStore.getInt(Keys.startingTab, 0)
    fun backReturnsToStart() = preferenceStore.getBoolean(Keys.backToStart, true)

    fun hasShownNotifPermission() = preferenceStore.getBoolean("has_shown_notification_permission", false)

    fun hasDeniedA11FilePermission() = preferenceStore.getBoolean(Keys.deniedA11FilePermission, false)

    fun clear() = prefs.edit().clear().apply()

    fun nightMode() = preferenceStore.getInt(Keys.nightMode, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun themeDarkAmoled() = preferenceStore.getBoolean(Keys.themeDarkAmoled, false)

    private val supportsDynamic = DynamicColors.isDynamicColorAvailable()
    fun lightTheme() = preferenceStore.getEnum(Keys.lightTheme, if (supportsDynamic) Themes.MONET else Themes.DEFAULT)
    fun darkTheme() = preferenceStore.getEnum(Keys.darkTheme, if (supportsDynamic) Themes.MONET else Themes.DEFAULT)

    fun pageTransitions() = preferenceStore.getBoolean(Keys.enableTransitions, true)

    fun pagerCutoutBehavior() = preferenceStore.getInt(Keys.pagerCutoutBehavior, 0)

    fun landscapeCutoutBehavior() = preferenceStore.getInt("landscape_cutout_behavior", 0)

    fun doubleTapAnimSpeed() = preferenceStore.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = preferenceStore.getBoolean(Keys.showPageNumber, true)

    fun trueColor() = preferenceStore.getBoolean(Keys.trueColor, false)

    fun fullscreen() = preferenceStore.getBoolean(Keys.fullscreen, true)

    fun keepScreenOn() = preferenceStore.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = preferenceStore.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = preferenceStore.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = preferenceStore.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = preferenceStore.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = preferenceStore.getInt(Keys.colorFilterMode, 0)

    fun defaultReadingMode() = prefs.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = preferenceStore.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = preferenceStore.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = preferenceStore.getInt(Keys.zoomStart, 1)

    fun readerTheme() = preferenceStore.getInt(Keys.readerTheme, 2)

    fun cropBorders() = preferenceStore.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = preferenceStore.getBoolean(Keys.cropBordersWebtoon, false)

    fun navigateToPan() = preferenceStore.getBoolean("navigate_pan", true)

    fun landscapeZoom() = preferenceStore.getBoolean("landscape_zoom", false)

    fun grayscale() = preferenceStore.getBoolean("pref_grayscale", false)

    fun invertedColors() = preferenceStore.getBoolean("pref_inverted_colors", false)

    fun webtoonSidePadding() = preferenceStore.getInt(Keys.webtoonSidePadding, 0)

    fun webtoonEnableZoomOut() = preferenceStore.getBoolean(Keys.webtoonEnableZoomOut, false)

    fun readWithLongTap() = preferenceStore.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = preferenceStore.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = preferenceStore.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun navigationModePager() = preferenceStore.getInt(Keys.navigationModePager, 0)

    fun navigationModeWebtoon() = preferenceStore.getInt(Keys.navigationModeWebtoon, 0)

    fun pagerNavInverted() = preferenceStore.getEnum(Keys.pagerNavInverted, ViewerNavigation.TappingInvertMode.NONE)

    fun webtoonNavInverted() = preferenceStore.getEnum(Keys.webtoonNavInverted, ViewerNavigation.TappingInvertMode.NONE)

    fun pageLayout() = preferenceStore.getInt(Keys.pageLayout, PageLayout.AUTOMATIC.value)

    fun automaticSplitsPage() = preferenceStore.getBoolean(Keys.automaticSplitsPage, false)

    fun invertDoublePages() = preferenceStore.getBoolean(Keys.invertDoublePages, false)

    fun webtoonPageLayout() = preferenceStore.getInt(Keys.webtoonPageLayout, PageLayout.SINGLE_PAGE.value)

    fun webtoonReaderHideThreshold() = preferenceStore.getEnum("reader_hide_threshold", Values.ReaderHideThreshold.LOW)

    fun webtoonInvertDoublePages() = preferenceStore.getBoolean(Keys.webtoonInvertDoublePages, false)

    fun readerBottomButtons() = preferenceStore.getStringSet(
        Keys.readerBottomButtons,
        ReaderBottomButton.BUTTONS_DEFAULTS,
    )

    fun showNavigationOverlayNewUser() = preferenceStore.getBoolean(Keys.showNavigationOverlayNewUser, true)

    fun showNavigationOverlayNewUserWebtoon() = preferenceStore.getBoolean(Keys.showNavigationOverlayNewUserWebtoon, true)

    fun preloadSize() = preferenceStore.getInt(Keys.preloadSize, 6)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun trackMarkedAsRead() = prefs.getBoolean(Keys.trackMarkedAsRead, false)

    fun trackingsToAddOnline() = preferenceStore.getStringSet(Keys.trackingsToAddOnline, emptySet())

    // TODO: SourcePref
    fun lastUsedCatalogueSource() = preferenceStore.getLong(Keys.lastUsedCatalogueSource, -1)

    fun lastUsedCategory() = preferenceStore.getInt(Keys.lastUsedCategory, 0)

    // TODO: SourcePref
    fun lastUsedSources() = preferenceStore.getStringSet("last_used_sources", emptySet())

    fun lastVersionCode() = preferenceStore.getInt("last_version_code", 0)

    fun browseAsList() = preferenceStore.getBoolean(Keys.catalogueAsList, false)

    // TODO: SourcePref
    fun enabledLanguages() = preferenceStore.getStringSet(
        Keys.enabledLanguages,
        setOfNotNull("all", "en", Locale.getDefault().language.takeIf { !it.startsWith("en") }),
    )

    // TODO: SourcePref
    fun sourceSorting() = preferenceStore.getInt(Keys.sourcesSort, 0)

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", "POINT_10")

    fun backupsDirectory() = preferenceStore.getString(Keys.backupDirectory, defaultBackupDir.toString())

    fun dateFormat(format: String = preferenceStore.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun appLanguage() = preferenceStore.getString("app_language", "")

    fun downloadsDirectory() = preferenceStore.getString(Keys.downloadsDirectory, defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun folderPerManga() = preferenceStore.getBoolean("create_folder_per_manga", false)

    fun librarySearchSuggestion() = preferenceStore.getString(Keys.librarySearchSuggestion, "")

    fun showLibrarySearchSuggestions() = preferenceStore.getBoolean(Keys.showLibrarySearchSuggestions, false)

    fun lastLibrarySuggestion() = preferenceStore.getLong("last_library_suggestion", 0L)

    fun numberOfBackups() = preferenceStore.getInt(Keys.numberOfBackups, 2)

    fun backupInterval() = preferenceStore.getInt(Keys.backupInterval, 0)
    fun removeAfterReadSlots() = preferenceStore.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun libraryUpdateInterval() = preferenceStore.getInt(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateLastTimestamp() = preferenceStore.getLong("library_update_last_timestamp", 0L)

    fun libraryUpdateDeviceRestriction() = preferenceStore.getStringSet("library_update_restriction", setOf(DEVICE_ONLY_ON_WIFI))

    fun libraryUpdateMangaRestriction() = preferenceStore.getStringSet("library_update_manga_restriction", setOf(MANGA_HAS_UNREAD, MANGA_NON_COMPLETED, MANGA_NON_READ))

    fun libraryUpdateCategories() = preferenceStore.getStringSet("library_update_categories", emptySet())
    fun libraryUpdateCategoriesExclude() = preferenceStore.getStringSet("library_update_categories_exclude", emptySet())

    fun libraryLayout() = preferenceStore.getInt(Keys.libraryLayout, LibraryItem.LAYOUT_COMFORTABLE_GRID)

    fun gridSize() = preferenceStore.getFloat(Keys.gridSize, 1f)

    fun uniformGrid() = preferenceStore.getBoolean(Keys.uniformGrid, true)

    fun outlineOnCovers() = preferenceStore.getBoolean(Keys.outlineOnCovers, true)

    fun downloadBadge() = preferenceStore.getBoolean(Keys.downloadBadge, false)

    fun languageBadge() = preferenceStore.getBoolean(Keys.languageBadge, false)

    fun filterDownloaded() = preferenceStore.getInt(Keys.filterDownloaded, 0)

    fun filterUnread() = preferenceStore.getInt(Keys.filterUnread, 0)

    fun filterCompleted() = preferenceStore.getInt(Keys.filterCompleted, 0)

    fun filterBookmarked() = preferenceStore.getInt("pref_filter_bookmarked_key", 0)

    fun filterTracked() = preferenceStore.getInt(Keys.filterTracked, 0)

    fun filterMangaType() = preferenceStore.getInt(Keys.filterMangaType, 0)

    fun filterContentType() = preferenceStore.getInt("pref_filter_content_type_key", 0)

    fun showEmptyCategoriesWhileFiltering() = preferenceStore.getBoolean(Keys.showEmptyCategoriesFiltering, false)

    fun librarySortingMode() = preferenceStore.getInt("library_sorting_mode", 0)

    fun librarySortingAscending() = preferenceStore.getBoolean("library_sorting_ascending", true)

    fun automaticExtUpdates() = preferenceStore.getBoolean(Keys.automaticExtUpdates, true)

    // TODO: SourcePref
    fun installedExtensionsOrder() = preferenceStore.getInt(Keys.installedExtensionsOrder, InstalledExtensionsOrder.Name.value)

    // TODO: SourcePref
    fun migrationSourceOrder() = preferenceStore.getInt("migration_source_order", Values.MigrationSourceOrder.Alphabetically.value)

    fun collapsedCategories() = preferenceStore.getStringSet("collapsed_categories", mutableSetOf())

    fun collapsedDynamicCategories() = preferenceStore.getStringSet("collapsed_dynamic_categories", mutableSetOf())

    fun collapsedDynamicAtBottom() = preferenceStore.getBoolean("collapsed_dynamic_at_bottom", false)

    // TODO: SourcePref
    fun hiddenSources() = preferenceStore.getStringSet("hidden_catalogues", mutableSetOf())

    // TODO: SourcePref
    fun pinnedCatalogues() = preferenceStore.getStringSet("pinned_catalogues", mutableSetOf())

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", false)

    fun downloadNewChapters() = preferenceStore.getBoolean(Keys.downloadNew, false)

    fun downloadNewChaptersInCategories() = preferenceStore.getStringSet("download_new_categories", emptySet())
    fun excludeCategoriesInDownloadNew() = preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -2)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun skipDupe() = preferenceStore.getBoolean("skip_dupe", false)

    fun useBiometrics() = preferenceStore.getBoolean(Keys.useBiometrics, false)

    fun lockAfter() = preferenceStore.getInt(Keys.lockAfter, 0)

    fun lastUnlock() = preferenceStore.getLong(Keys.lastUnlock, 0)

    fun secureScreen() = preferenceStore.getEnum("secure_screen_v2", Values.SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun removeArticles() = preferenceStore.getBoolean(Keys.removeArticles, false)

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    // using string instead of set so it is ordered
    // TODO: SourcePref
    fun migrationSources() = preferenceStore.getString("migrate_sources", "")

    // TODO: SourcePref
    fun useSourceWithMost() = preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = preferenceStore.getBoolean(Keys.skipPreMigration, false)

    fun defaultMangaOrder() = preferenceStore.getString("default_manga_order", "")

    fun refreshCoversToo() = preferenceStore.getBoolean(Keys.refreshCoversToo, true)

    // TODO: SourcePref
    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun recentsViewType() = preferenceStore.getInt("recents_view_type", 0)

    fun showRecentsDownloads() = preferenceStore.getEnum(Keys.showDLsInRecents, RecentMangaAdapter.ShowRecentsDLs.All)

    fun showRecentsRemHistory() = preferenceStore.getBoolean(Keys.showRemHistoryInRecents, true)

    fun showReadInAllRecents() = preferenceStore.getBoolean(Keys.showReadInAllRecents, false)

    fun showUpdatedTime() = preferenceStore.getBoolean(Keys.showUpdatedTime, false)

    fun sortFetchedTime() = preferenceStore.getBoolean("sort_fetched_time", false)

    fun collapseGroupedUpdates() = preferenceStore.getBoolean("group_chapters_updates", false)

    fun groupChaptersHistory() = preferenceStore.getEnum("group_chapters_history_type", RecentsPresenter.GroupType.ByWeek)

    fun collapseGroupedHistory() = preferenceStore.getBoolean("collapse_group_history", true)

    fun showTitleFirstInRecents() = preferenceStore.getBoolean(Keys.showTitleFirstInRecents, false)

    fun lastExtCheck() = preferenceStore.getLong("last_ext_check", 0)

    fun lastAppCheck() = preferenceStore.getLong("last_app_check", 0)

    fun checkForBetas() = preferenceStore.getBoolean("check_for_betas", BuildConfig.BETA)

    fun unreadBadgeType() = preferenceStore.getInt("unread_badge_type", 2)

    fun categoryNumberOfItems() = preferenceStore.getBoolean(Keys.categoryNumberOfItems, false)

    fun hideStartReadingButton() = preferenceStore.getBoolean("hide_reading_button", false)

    fun alwaysShowChapterTransition() = preferenceStore.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun deleteRemovedChapters() = preferenceStore.getInt(Keys.deleteRemovedChapters, 0)

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun showAllCategories() = preferenceStore.getBoolean("show_all_categories", true)

    fun showAllCategoriesWhenSearchingSingleCategory() = preferenceStore.getBoolean("show_all_categories_when_searching_single_category", false)

    fun hopperGravity() = preferenceStore.getInt("hopper_gravity", 1)

    fun filterOrder() = preferenceStore.getString("filter_order", FilterBottomSheet.Filters.DEFAULT_ORDER)

    fun hopperLongPressAction() = preferenceStore.getInt(Keys.hopperLongPress, 0)

    fun hideHopper() = preferenceStore.getBoolean("hide_hopper", false)

    fun autohideHopper() = preferenceStore.getBoolean(Keys.autoHideHopper, true)

    fun groupLibraryBy() = preferenceStore.getInt("group_library_by", 0)

    fun showCategoryInTitle() = preferenceStore.getBoolean("category_in_title", false)

    fun onlySearchPinned() = preferenceStore.getBoolean(Keys.onlySearchPinned, false)

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // Tutorial preferences
    fun shownFilterTutorial() = preferenceStore.getBoolean("shown_filter_tutorial", false)

    fun shownChapterSwipeTutorial() = preferenceStore.getBoolean("shown_swipe_tutorial", false)

    fun shownDownloadQueueTutorial() = preferenceStore.getBoolean("shown_download_queue", false)

    fun shownLongPressCategoryTutorial() = preferenceStore.getBoolean("shown_long_press_category", false)

    fun shownHopperSwipeTutorial() = preferenceStore.getBoolean("shown_hopper_swipe", false)

    fun shownDownloadSwipeTutorial() = preferenceStore.getBoolean("shown_download_tutorial", false)

    fun hideBottomNavOnScroll() = preferenceStore.getBoolean(Keys.hideBottomNavOnScroll, true)

    fun sideNavIconAlignment() = preferenceStore.getInt(Keys.sideNavIconAlignment, 1)

    // TODO: SourcePref
    fun showNsfwSources() = preferenceStore.getBoolean(Keys.showNsfwSource, true)

    fun themeMangaDetails() = prefs.getBoolean(Keys.themeMangaDetails, true)

    fun useLargeToolbar() = preferenceStore.getBoolean("use_large_toolbar", true)

    fun dohProvider() = prefs.getInt(Keys.dohProvider, -1)

    fun defaultUserAgent() = preferenceStore.getString("default_user_agent", NetworkHelper.DEFAULT_USER_AGENT)

    fun showSeriesInShortcuts() = prefs.getBoolean(Keys.showSeriesInShortcuts, true)
    fun showSourcesInShortcuts() = prefs.getBoolean(Keys.showSourcesInShortcuts, true)
    fun openChapterInShortcuts() = prefs.getBoolean(Keys.openChapterInShortcuts, true)

    fun incognitoMode() = preferenceStore.getBoolean(Keys.incognitoMode, false)

    fun hasPromptedBeforeUpdateAll() = preferenceStore.getBoolean("has_prompted_update_all", false)

    fun sideNavMode() = preferenceStore.getInt(Keys.sideNavMode, 0)

    fun appShouldAutoUpdate() = prefs.getInt(Keys.shouldAutoUpdate, AppDownloadInstallJob.ONLY_ON_UNMETERED)

    // TODO: SourcePref
    fun autoUpdateExtensions() = prefs.getInt(Keys.autoUpdateExtensions, AppDownloadInstallJob.ONLY_ON_UNMETERED)

    fun extensionInstaller() = preferenceStore.getInt("extension_installer", ExtensionInstaller.PACKAGE_INSTALLER)

    fun filterChapterByRead() = preferenceStore.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = preferenceStore.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = preferenceStore.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun sortChapterOrder() = preferenceStore.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.CHAPTER_SORTING_SOURCE)

    fun hideChapterTitlesByDefault() = preferenceStore.getBoolean(Keys.hideChapterTitles, false)

    fun chaptersDescAsDefault() = preferenceStore.getBoolean(Keys.chaptersDescAsDefault, true)

    fun sortChapterByAscendingOrDescending() = prefs.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.CHAPTER_SORT_DESC)

    fun coverRatios() = preferenceStore.getStringSet(Keys.coverRatios, emptySet())

    fun coverColors() = preferenceStore.getStringSet(Keys.coverColors, emptySet())

    fun useStaggeredGrid() = preferenceStore.getBoolean("use_staggered_grid", false)
}
