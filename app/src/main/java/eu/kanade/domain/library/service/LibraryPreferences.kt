package eu.kanade.domain.library.service

import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.library.model.LibrarySort
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.widget.ExtendedNavigationView

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun libraryDisplayMode() = preferenceStore.getObject("pref_display_mode_library", LibraryDisplayMode.default, LibraryDisplayMode.Serializer::serialize, LibraryDisplayMode.Serializer::deserialize)

    fun librarySortingMode() = preferenceStore.getObject("library_sorting_mode", LibrarySort.default, LibrarySort.Serializer::serialize, LibrarySort.Serializer::deserialize)

    fun portraitColumns() = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    fun libraryUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 24)
    fun libraryUpdateLastTimestamp() = preferenceStore.getLong("library_update_last_timestamp", 0L)

    fun libraryUpdateDeviceRestriction() = preferenceStore.getStringSet("library_update_restriction", setOf(DEVICE_ONLY_ON_WIFI))
    fun libraryUpdateMangaRestriction() = preferenceStore.getStringSet("library_update_manga_restriction", setOf(MANGA_HAS_UNREAD, MANGA_NON_COMPLETED, MANGA_NON_READ))

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun autoUpdateTrackers() = preferenceStore.getBoolean("auto_update_trackers", false)

    // region Filter

    fun filterDownloaded() = preferenceStore.getInt("pref_filter_library_downloaded", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterUnread() = preferenceStore.getInt("pref_filter_library_unread", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterStarted() = preferenceStore.getInt("pref_filter_library_started", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterCompleted() = preferenceStore.getInt("pref_filter_library_completed", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterTracking(name: Int) = preferenceStore.getInt("pref_filter_library_tracked_$name", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    // endregion

    // region Badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun unreadBadge() = preferenceStore.getBoolean("display_unread_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", false)

    fun showUpdatesNavBadge() = preferenceStore.getBoolean("library_update_show_tab_badge", false)
    fun unreadUpdatesCount() = preferenceStore.getInt("library_unread_updates_count", 0)

    // endregion

    // region Category

    fun defaultCategory() = preferenceStore.getInt("default_category", -1)

    fun lastUsedCategory() = preferenceStore.getInt("last_used_category", 0)

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    fun libraryUpdateCategories() = preferenceStore.getStringSet("library_update_categories", emptySet())

    fun libraryUpdateCategoriesExclude() = preferenceStore.getStringSet("library_update_categories_exclude", emptySet())

    // endregion
}
