package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.util.system.isDevFlavor
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_BATTERY_NOT_LOW
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.manga.interactor.MAX_GRACE_PERIOD
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = runBlocking { getCategories.await() })

        return listOf(
            getCategoriesGroup(LocalNavigator.currentOrThrow, allCategories, libraryPreferences),
            getGlobalUpdateGroup(allCategories, libraryPreferences),
            getChapterSwipeActionsGroup(libraryPreferences),
        )
    }

    @Composable
    private fun getCategoriesGroup(
        navigator: Navigator,
        allCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val userCategoriesCount = allCategories.filterNot(Category::isSystemCategory).size

        val defaultCategory by libraryPreferences.defaultCategory().collectAsState()
        val selectedCategory = allCategories.find { it.id == defaultCategory.toLong() }

        // For default category
        val ids = listOf(libraryPreferences.defaultCategory().defaultValue()) +
            allCategories.fastMap { it.id.toInt() }
        val labels = listOf(stringResource(R.string.default_category_summary)) +
            allCategories.fastMap { it.visualName(context) }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.categories),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.action_edit_categories),
                    subtitle = pluralStringResource(
                        id = R.plurals.num_categories,
                        count = userCategoriesCount,
                        userCategoriesCount,
                    ),
                    onClick = { navigator.push(CategoryScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.defaultCategory(),
                    title = stringResource(R.string.default_category),
                    subtitle = selectedCategory?.visualName ?: stringResource(R.string.default_category_summary),
                    entries = ids.zip(labels).toMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.categorizedDisplaySettings(),
                    title = stringResource(R.string.categorized_display_settings),
                    onValueChanged = {
                        if (!it) {
                            scope.launch {
                                Injekt.get<ResetCategoryFlags>().await()
                            }
                        }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getGlobalUpdateGroup(
        allCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val libraryUpdateIntervalPref = libraryPreferences.libraryUpdateInterval()
        val libraryUpdateDeviceRestrictionPref = libraryPreferences.libraryUpdateDeviceRestriction()
        val libraryUpdateMangaRestrictionPref = libraryPreferences.libraryUpdateMangaRestriction()
        val libraryUpdateCategoriesPref = libraryPreferences.libraryUpdateCategories()
        val libraryUpdateCategoriesExcludePref = libraryPreferences.libraryUpdateCategoriesExclude()

        val libraryUpdateInterval by libraryUpdateIntervalPref.collectAsState()
        val libraryUpdateMangaRestriction by libraryUpdateMangaRestrictionPref.collectAsState()

        val included by libraryUpdateCategoriesPref.collectAsState()
        val excluded by libraryUpdateCategoriesExcludePref.collectAsState()
        var showCategoriesDialog by rememberSaveable { mutableStateOf(false) }
        if (showCategoriesDialog) {
            TriStateListDialog(
                title = stringResource(R.string.categories),
                message = stringResource(R.string.pref_library_update_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showCategoriesDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    libraryUpdateCategoriesPref.set(newIncluded.map { it.id.toString() }.toSet())
                    libraryUpdateCategoriesExcludePref.set(newExcluded.map { it.id.toString() }.toSet())
                    showCategoriesDialog = false
                },
            )
        }
        val leadRange by libraryPreferences.leadingExpectedDays().collectAsState()
        val followRange by libraryPreferences.followingExpectedDays().collectAsState()

        var showFetchRangesDialog by rememberSaveable { mutableStateOf(false) }
        if (showFetchRangesDialog) {
            LibraryExpectedRangeDialog(
                initialLead = leadRange,
                initialFollow = followRange,
                onDismissRequest = { showFetchRangesDialog = false },
                onValueChanged = { leadValue, followValue ->
                    libraryPreferences.leadingExpectedDays().set(leadValue)
                    libraryPreferences.followingExpectedDays().set(followValue)
                    showFetchRangesDialog = false
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_library_update),
            preferenceItems = listOfNotNull(
                Preference.PreferenceItem.ListPreference(
                    pref = libraryUpdateIntervalPref,
                    title = stringResource(R.string.pref_library_update_interval),
                    entries = mapOf(
                        0 to stringResource(R.string.update_never),
                        12 to stringResource(R.string.update_12hour),
                        24 to stringResource(R.string.update_24hour),
                        48 to stringResource(R.string.update_48hour),
                        72 to stringResource(R.string.update_72hour),
                        168 to stringResource(R.string.update_weekly),
                    ),
                    onValueChanged = {
                        LibraryUpdateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = libraryUpdateDeviceRestrictionPref,
                    enabled = libraryUpdateInterval > 0,
                    title = stringResource(R.string.pref_library_update_restriction),
                    subtitle = stringResource(R.string.restrictions),
                    entries = mapOf(
                        DEVICE_ONLY_ON_WIFI to stringResource(R.string.connected_to_wifi),
                        DEVICE_NETWORK_NOT_METERED to stringResource(R.string.network_not_metered),
                        DEVICE_CHARGING to stringResource(R.string.charging),
                        DEVICE_BATTERY_NOT_LOW to stringResource(R.string.battery_not_low),
                    ),
                    onValueChanged = {
                        // Post to event looper to allow the preference to be updated.
                        ContextCompat.getMainExecutor(context).execute { LibraryUpdateJob.setupTask(context) }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showCategoriesDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoUpdateMetadata(),
                    title = stringResource(R.string.pref_library_update_refresh_metadata),
                    subtitle = stringResource(R.string.pref_library_update_refresh_metadata_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoUpdateTrackers(),
                    enabled = Injekt.get<TrackManager>().hasLoggedServices(),
                    title = stringResource(R.string.pref_library_update_refresh_trackers),
                    subtitle = stringResource(R.string.pref_library_update_refresh_trackers_summary),
                ),
                // TODO: remove isDevFlavor checks once functionality is available
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = libraryUpdateMangaRestrictionPref,
                    title = stringResource(R.string.pref_library_update_manga_restriction),
                    entries = buildMap {
                        put(MANGA_HAS_UNREAD, stringResource(R.string.pref_update_only_completely_read))
                        put(MANGA_NON_READ, stringResource(R.string.pref_update_only_started))
                        put(MANGA_NON_COMPLETED, stringResource(R.string.pref_update_only_non_completed))
                        if (isDevFlavor) {
                            put(MANGA_OUTSIDE_RELEASE_PERIOD, stringResource(R.string.pref_update_only_in_release_period))
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_update_release_grace_period),
                    subtitle = listOf(
                        pluralStringResource(R.plurals.pref_update_release_leading_days, leadRange, leadRange),
                        pluralStringResource(R.plurals.pref_update_release_following_days, followRange, followRange),
                    ).joinToString(),
                    onClick = { showFetchRangesDialog = true },
                ).takeIf { MANGA_OUTSIDE_RELEASE_PERIOD in libraryUpdateMangaRestriction && isDevFlavor },
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(R.string.pref_update_release_grace_period_info),
                ).takeIf { MANGA_OUTSIDE_RELEASE_PERIOD in libraryUpdateMangaRestriction && isDevFlavor },
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.newShowUpdatesCount(),
                    title = stringResource(R.string.pref_library_update_show_tab_badge),
                ),
            ),
        )
    }

    @Composable
    private fun getChapterSwipeActionsGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_chapter_swipe),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.swipeToStartAction(),
                    title = stringResource(R.string.pref_chapter_swipe_start),
                    entries = mapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to stringResource(R.string.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to stringResource(R.string.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to stringResource(R.string.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to stringResource(R.string.action_download),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.swipeToEndAction(),
                    title = stringResource(R.string.pref_chapter_swipe_end),
                    entries = mapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to stringResource(R.string.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to stringResource(R.string.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to stringResource(R.string.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to stringResource(R.string.action_download),
                    ),
                ),
            ),
        )
    }
    	
    @Composable
    private fun LibraryExpectedRangeDialog(
        initialLead: Int,
        initialFollow: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (portrait: Int, landscape: Int) -> Unit,
    ) {
        var leadValue by rememberSaveable { mutableIntStateOf(initialLead) }
        var followValue by rememberSaveable { mutableIntStateOf(initialFollow) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.pref_update_release_grace_period)) },
            text = {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = pluralStringResource(R.plurals.pref_update_release_leading_days, leadValue, leadValue),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = pluralStringResource(R.plurals.pref_update_release_following_days, followValue, followValue),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val size = DpSize(width = maxWidth / 2, height = 128.dp)
                    val items = (0..MAX_GRACE_PERIOD).map(Int::toString)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WheelTextPicker(
                            size = size,
                            items = items,
                            startIndex = leadValue,
                            onSelectionChanged = {
                                leadValue = it
                            },
                        )
                        WheelTextPicker(
                            size = size,
                            items = items,
                            startIndex = followValue,
                            onSelectionChanged = {
                                followValue = it
                            },
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = { onValueChanged(leadValue, followValue) }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
        )
    }
}
