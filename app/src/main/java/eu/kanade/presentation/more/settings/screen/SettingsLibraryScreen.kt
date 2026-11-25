// Adapted from https://github.com/komikku-app/komikku/pull/1356
// Copyright Komikku contributors, Apache-2.0

package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import eu.kanade.presentation.category.buildCategoryAncestorIds
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.components.CategoryExpandButton
import eu.kanade.presentation.category.visibleCategoryHierarchy
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mihon.app.di.appGraph
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.LocalNavigator
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_EXISTING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_NEW
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Serializable
data object SettingsLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val getCategories = remember { context.appGraph.getCategories }
        val libraryPreferences = remember { context.appGraph.libraryPreferences }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        return listOf(
            getCategoriesGroup(allCategories, libraryPreferences),
            getGlobalUpdateGroup(allCategories, libraryPreferences),
            getBehaviorGroup(libraryPreferences),
        )
    }

    @Composable
    private fun getCategoriesGroup(
        allCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()
        val userCategoriesCount = allCategories.filterNot(Category::isSystemCategory).size

        var showDefaultDialog by rememberSaveable { mutableStateOf(false) }

        if (showDefaultDialog) {
            DefaultCategoryDialog(
                categories = allCategories,
                selectedId = libraryPreferences.defaultCategory.get(),
                askEveryTimeId = libraryPreferences.defaultCategory.defaultValue(),
                onDismiss = { showDefaultDialog = false },
                onConfirm = {
                    libraryPreferences.defaultCategory.set(it)
                    showDefaultDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.categories),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.action_edit_categories),
                    subtitle = pluralStringResource(
                        MR.plurals.num_categories,
                        count = userCategoriesCount,
                        userCategoriesCount,
                    ),
                    onClick = { navigator.push(CategoryRoute) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.default_category),
                    subtitle = getDefaultCategoryLabel(allCategories, libraryPreferences),
                    onClick = { showDefaultDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.parentChildLayout,
                    title = stringResource(MR.strings.pref_library_parent_child_layout),
                    subtitle = stringResource(MR.strings.pref_library_parent_child_layout_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.categorizedDisplaySettings,
                    title = stringResource(MR.strings.categorized_display_settings),
                    onValueChanged = {
                        if (!it) {
                            scope.launch {
                                context.appGraph.resetCategoryFlags.await()
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

        val autoUpdateIntervalPref = libraryPreferences.autoUpdateInterval
        val autoUpdateCategoriesPref = libraryPreferences.updateCategories
        val autoUpdateCategoriesExcludePref = libraryPreferences.updateCategoriesExclude

        val autoUpdateInterval by autoUpdateIntervalPref.collectAsState()

        val included by autoUpdateCategoriesPref.collectAsState()
        val excluded by autoUpdateCategoriesExcludePref.collectAsState()
        var showCategoriesDialog by rememberSaveable { mutableStateOf(false) }
        if (showCategoriesDialog) {
            UpdateCategoriesDialog(
                categories = allCategories,
                included = included,
                excluded = excluded,
                onDismiss = { showCategoriesDialog = false },
                onConfirm = { newIncluded, newExcluded ->
                    autoUpdateCategoriesPref.set(newIncluded)
                    autoUpdateCategoriesExcludePref.set(newExcluded)
                    showCategoriesDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = autoUpdateIntervalPref,
                    entries = mapOf(
                        0 to stringResource(MR.strings.update_never),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        72 to stringResource(MR.strings.update_72hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(MR.strings.pref_library_update_interval),
                    onValueChanged = {
                        LibraryUpdateWorker.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.autoUpdateDeviceRestrictions,
                    entries = mapOf(
                        DEVICE_ONLY_ON_WIFI to stringResource(MR.strings.connected_to_wifi),
                        DEVICE_NETWORK_NOT_METERED to stringResource(MR.strings.network_not_metered),
                        DEVICE_CHARGING to stringResource(MR.strings.charging),
                    ),
                    title = stringResource(MR.strings.pref_library_update_restriction),
                    subtitle = stringResource(MR.strings.restrictions),
                    visible = autoUpdateInterval > 0,
                    onValueChanged = {
                        // Post to event looper to allow the preference to be updated.
                        ContextCompat.getMainExecutor(context).execute {
                            LibraryUpdateWorker.setupTask(context)
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showCategoriesDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.autoUpdateMetadata,
                    title = stringResource(MR.strings.pref_library_update_refresh_metadata),
                    subtitle = stringResource(MR.strings.pref_library_update_refresh_metadata_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.autoUpdateMangaRestrictions,
                    entries = mapOf(
                        MANGA_HAS_UNREAD to stringResource(MR.strings.pref_update_only_completely_read),
                        MANGA_NON_READ to stringResource(MR.strings.pref_update_only_started),
                        MANGA_NON_COMPLETED to stringResource(MR.strings.pref_update_only_non_completed),
                        MANGA_OUTSIDE_RELEASE_PERIOD to stringResource(MR.strings.pref_update_only_in_release_period),
                    ),
                    title = stringResource(MR.strings.pref_library_update_smart_update),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.newShowUpdatesCount,
                    title = stringResource(MR.strings.pref_library_update_show_tab_badge),
                ),
            ),
        )
    }

    @Composable
    private fun DefaultCategoryDialog(
        categories: List<Category>,
        selectedId: Int,
        askEveryTimeId: Int,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit,
    ) {
        var selected by rememberSaveable { mutableStateOf(selectedId) }
        CategorySelectionDialog(
            title = stringResource(MR.strings.default_category),
            onDismiss = onDismiss,
            onConfirm = { onConfirm(selected) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { selected = askEveryTimeId },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == askEveryTimeId, onClick = { selected = askEveryTimeId })
                Text(stringResource(MR.strings.default_category_summary))
            }
            CategorySelectionRows(
                categories = categories,
                initiallySelected = listOf(selectedId.toLong()),
                onSelect = { selected = it.id.toInt() },
            ) { category ->
                RadioButton(selected = selected == category.id.toInt(), onClick = { selected = category.id.toInt() })
            }
        }
    }

    @Composable
    private fun UpdateCategoriesDialog(
        categories: List<Category>,
        included: Set<String>,
        excluded: Set<String>,
        onDismiss: () -> Unit,
        onConfirm: (Set<String>, Set<String>) -> Unit,
    ) {
        var includedSet by rememberSaveable { mutableStateOf(included) }
        var excludedSet by rememberSaveable { mutableStateOf(excluded) }
        fun toggle(category: Category) {
            val id = category.id.toString()
            when (id) {
                in includedSet -> {
                    includedSet -= id
                    excludedSet += id
                }
                in excludedSet -> excludedSet -= id
                else -> includedSet += id
            }
        }
        CategorySelectionDialog(
            title = stringResource(MR.strings.categories),
            onDismiss = onDismiss,
            onConfirm = { onConfirm(includedSet, excludedSet) },
        ) {
            Text(stringResource(MR.strings.pref_library_update_categories_details))
            CategorySelectionRows(
                categories = categories,
                initiallySelected = (included + excluded).mapNotNull(String::toLongOrNull),
                onSelect = ::toggle,
            ) { category ->
                TriStateCheckbox(
                    state = when (category.id.toString()) {
                        in includedSet -> ToggleableState.On
                        in excludedSet -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { toggle(category) },
                )
            }
        }
    }

    @Composable
    private fun CategorySelectionDialog(
        title: String,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(MR.strings.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
            },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        )
    }

    @Composable
    private fun CategorySelectionRows(
        categories: List<Category>,
        initiallySelected: List<Long>,
        onSelect: (Category) -> Unit,
        control: @Composable (Category) -> Unit,
    ) {
        var expanded by rememberSaveable {
            mutableStateOf(buildCategoryAncestorIds(categories, initiallySelected))
        }
        val hierarchy = remember(categories) { buildCategoryHierarchy(categories) }
        val parents = remember(categories) { categories.mapNotNullTo(mutableSetOf()) { it.parentId } }
        visibleCategoryHierarchy(hierarchy, expanded).forEach { (category, depth) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(start = (depth * 24).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                control(category)
                Text(category.visualName, Modifier.weight(1f))
                if (category.id in parents) {
                    val isExpanded = category.id in expanded
                    CategoryExpandButton(isExpanded) {
                        expanded = if (isExpanded) expanded - category.id else expanded + category.id
                    }
                }
            }
        }
    }

    @Composable
    private fun getDefaultCategoryLabel(
        categories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): String {
        val defaultId by libraryPreferences.defaultCategory.collectAsState()
        return categories.firstOrNull { it.id.toInt() == defaultId }?.visualName
            ?: stringResource(MR.strings.default_category_summary)
    }

    @Composable
    private fun getBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_behavior),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeToStartAction,
                    entries = mapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to
                            stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeToEndAction,
                    entries = mapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to
                            stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateReadChapterAsRead,
                    entries = mapOf(
                        MARK_DUPLICATE_CHAPTER_READ_EXISTING to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_existing),
                        MARK_DUPLICATE_CHAPTER_READ_NEW to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_new),
                    ),
                    title = stringResource(MR.strings.pref_mark_duplicate_read_chapter_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.hideMissingChapters,
                    title = stringResource(MR.strings.pref_hide_missing_chapter_indicators),
                ),
            ),
        )
    }
}
