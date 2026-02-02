package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: LibrarySettingsScreenModel,
    category: Category?,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
            "Tags",
            "Extensions",
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    screenModel = screenModel,
                )
                1 -> SortPage(
                    category = category,
                    screenModel = screenModel,
                )
                2 -> DisplayPage(
                    screenModel = screenModel,
                )
                3 -> TagsPage(
                    screenModel = screenModel,
                )
                4 -> ExtensionsPage(
                    screenModel = screenModel,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    screenModel: LibrarySettingsScreenModel,
) {
    val filterDownloaded by screenModel.libraryPreferences.filterDownloaded().collectAsState()
    val downloadedOnly by screenModel.preferences.downloadedOnly().collectAsState()
    val autoUpdateMangaRestrictions by screenModel.libraryPreferences.autoUpdateMangaRestrictions().collectAsState()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterDownloaded) },
    )
    val filterUnread by screenModel.libraryPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterUnread) },
    )
    val filterStarted by screenModel.libraryPreferences.filterStarted().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterStarted) },
    )
    val filterBookmarked by screenModel.libraryPreferences.filterBookmarked().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterBookmarked) },
    )
    val filterCompleted by screenModel.libraryPreferences.filterCompleted().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.completed),
        state = filterCompleted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterCompleted) },
    )
    // TODO: re-enable when custom intervals are ready for stable
    if ((!isReleaseBuildType) && LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in autoUpdateMangaRestrictions) {
        val filterIntervalCustom by screenModel.libraryPreferences.filterIntervalCustom().collectAsState()
        TriStateItem(
            label = stringResource(MR.strings.action_filter_interval_custom),
            state = filterIntervalCustom,
            onClick = { screenModel.toggleFilter(LibraryPreferences::filterIntervalCustom) },
        )
    }

    val trackers by screenModel.trackersFlow.collectAsState()
    when (trackers.size) {
        0 -> {
            // No trackers
        }
        1 -> {
            val service = trackers[0]
            val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
            TriStateItem(
                label = stringResource(MR.strings.action_filter_tracked),
                state = filterTracker,
                onClick = { screenModel.toggleTracker(service.id.toInt()) },
            )
        }
        else -> {
            HeadingItem(MR.strings.action_filter_tracked)
            trackers.map { service ->
                val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
                TriStateItem(
                    label = service.name,
                    state = filterTracker,
                    onClick = { screenModel.toggleTracker(service.id.toInt()) },
                )
            }
        }
    }

    // Search options section
    HeadingItem("Search Options")
    CheckboxItem(
        label = "Search chapter names",
        pref = screenModel.libraryPreferences.searchChapterNames(),
    )
    CheckboxItem(
        label = "Search novel descriptions and tags",
        pref = screenModel.libraryPreferences.searchChapterContent(),
    )
    CheckboxItem(
        label = "Search by URL",
        pref = screenModel.libraryPreferences.searchByUrl(),
    )
    CheckboxItem(
        label = "Use regex search",
        pref = screenModel.libraryPreferences.useRegexSearch(),
    )
}

@Composable
private fun ColumnScope.SortPage(
    category: Category?,
    screenModel: LibrarySettingsScreenModel,
) {
    val trackers by screenModel.trackersFlow.collectAsState()
    val sortingMode = category.sort.type
    val sortDescending = !category.sort.isAscending

    val options = remember(trackers.isEmpty()) {
        val trackerMeanPair = if (trackers.isNotEmpty()) {
            MR.strings.action_sort_tracker_score to LibrarySort.Type.TrackerMean
        } else {
            null
        }
        listOfNotNull(
            MR.strings.action_sort_alpha to LibrarySort.Type.Alphabetical,
            MR.strings.action_sort_total to LibrarySort.Type.TotalChapters,
            MR.strings.action_sort_downloaded to LibrarySort.Type.DownloadedChapters,
            MR.strings.action_sort_last_read to LibrarySort.Type.LastRead,
            MR.strings.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
            MR.strings.action_sort_unread_count to LibrarySort.Type.UnreadCount,
            MR.strings.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
            MR.strings.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
            MR.strings.action_sort_date_added to LibrarySort.Type.DateAdded,
            trackerMeanPair,
            MR.strings.action_sort_random to LibrarySort.Type.Random,
        )
    }

    options.map { (titleRes, mode) ->
        if (mode == LibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh
                    .takeIf { sortingMode == LibrarySort.Type.Random },
                onClick = {
                    screenModel.setSort(category, mode, LibrarySort.Direction.Ascending)
                },
            )
            return@map
        }
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        LibrarySort.Direction.Ascending
                    } else {
                        LibrarySort.Direction.Descending
                    }
                    else -> if (sortDescending) {
                        LibrarySort.Direction.Descending
                    } else {
                        LibrarySort.Direction.Ascending
                    }
                }
                screenModel.setSort(category, mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    screenModel: LibrarySettingsScreenModel,
) {
    val displayMode by screenModel.libraryPreferences.displayMode().collectAsState()
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { screenModel.setDisplayMode(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        val columnPreference = remember {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenModel.libraryPreferences.landscapeColumns()
            } else {
                screenModel.libraryPreferences.portraitColumns()
            }
        }

        val columns by columnPreference.collectAsState()
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueString = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = screenModel.libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = screenModel.libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = screenModel.libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = screenModel.libraryPreferences.languageBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_continue_reading_button),
        pref = screenModel.libraryPreferences.showContinueReadingButton(),
    )
    CheckboxItem(
        label = "Show URL in list view",
        pref = screenModel.libraryPreferences.showUrlInList(),
    )

    val titleMaxLines by screenModel.libraryPreferences.titleMaxLines().collectAsState()
    SliderItem(
        value = titleMaxLines,
        valueRange = 1..10,
        label = "Title Max Lines",
        valueString = titleMaxLines.toString(),
        onChange = screenModel.libraryPreferences.titleMaxLines()::set,
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_tabs),
        pref = screenModel.libraryPreferences.categoryTabs(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = screenModel.libraryPreferences.categoryNumberOfItems(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TagsPage(
    screenModel: LibrarySettingsScreenModel,
) {
    val tags by screenModel.tagsFlow.collectAsState()
    val includedTags by screenModel.libraryPreferences.includedTags().collectAsState()
    val excludedTags by screenModel.libraryPreferences.excludedTags().collectAsState()
    val filterNoTags by screenModel.libraryPreferences.filterNoTags().collectAsState()
    val noTagsCount by screenModel.noTagsCountFlow.collectAsState()
    val isLoading by screenModel.isLoading.collectAsState()
    
    // Tag options
    val tagIncludeModeAnd by screenModel.libraryPreferences.tagIncludeMode().collectAsState()
    val tagExcludeModeAnd by screenModel.libraryPreferences.tagExcludeMode().collectAsState()
    val tagSortByName by screenModel.libraryPreferences.tagSortByName().collectAsState()
    val tagSortAscending by screenModel.libraryPreferences.tagSortAscending().collectAsState()
    val tagCaseSensitive by screenModel.libraryPreferences.tagCaseSensitive().collectAsState()
    
    // Tag search state
    val tagSearchQuery by screenModel.tagSearchQuery.collectAsState()
    
    // Options expanded state
    val optionsExpanded by screenModel.tagOptionsExpanded.collectAsState()

    // Load data when first entering this page (only if empty)
    LaunchedEffect(Unit) {
        if (tags.isEmpty()) {
            screenModel.refreshTags()
        }
    }

    // Header row with refresh and options toggle
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { screenModel.toggleTagOptions() }) {
            Icon(
                imageVector = if (optionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (optionsExpanded) "Collapse options" else "Expand options",
            )
            Spacer(Modifier.width(4.dp))
            Text("Options")
        }
        TextButton(onClick = { screenModel.refreshTags(forceRefresh = true) }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            Spacer(Modifier.width(4.dp))
            Text("Refresh")
        }
    }
    
    // Collapsible options section
    if (optionsExpanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 4.dp),
        ) {
            // Include mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Include tags mode:", style = MaterialTheme.typography.bodyMedium)
                Row {
                    FilterChip(
                        selected = !tagIncludeModeAnd,
                        onClick = { screenModel.libraryPreferences.tagIncludeMode().set(false) },
                        label = { Text("OR") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = tagIncludeModeAnd,
                        onClick = { screenModel.libraryPreferences.tagIncludeMode().set(true) },
                        label = { Text("AND") },
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Exclude mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Exclude tags mode:", style = MaterialTheme.typography.bodyMedium)
                Row {
                    FilterChip(
                        selected = !tagExcludeModeAnd,
                        onClick = { screenModel.libraryPreferences.tagExcludeMode().set(false) },
                        label = { Text("OR") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = tagExcludeModeAnd,
                        onClick = { screenModel.libraryPreferences.tagExcludeMode().set(true) },
                        label = { Text("AND") },
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Sort options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sort by:", style = MaterialTheme.typography.bodyMedium)
                Row {
                    FilterChip(
                        selected = !tagSortByName,
                        onClick = { screenModel.libraryPreferences.tagSortByName().set(false) },
                        label = { Text("Count") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = tagSortByName,
                        onClick = { screenModel.libraryPreferences.tagSortByName().set(true) },
                        label = { Text("Name") },
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Sort direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sort order:", style = MaterialTheme.typography.bodyMedium)
                Row {
                    FilterChip(
                        selected = !tagSortAscending,
                        onClick = { screenModel.libraryPreferences.tagSortAscending().set(false) },
                        label = { Text("Desc") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = tagSortAscending,
                        onClick = { screenModel.libraryPreferences.tagSortAscending().set(true) },
                        label = { Text("Asc") },
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Case sensitivity toggle
            CheckboxItem(
                label = "Case sensitive matching",
                pref = screenModel.libraryPreferences.tagCaseSensitive(),
            )
        }
    }

    // Clear All button
    if (includedTags.isNotEmpty() || excludedTags.isNotEmpty() || filterNoTags != TriState.DISABLED) {
        TextButton(
            onClick = { screenModel.clearAllTagFilters() },
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
        ) {
            Icon(Icons.Default.Clear, contentDescription = "Clear all")
            Spacer(Modifier.width(4.dp))
            Text("Clear All Filters")
        }
    }

    // No tags filter
    TriStateItem(
        label = "No tags ($noTagsCount)",
        state = filterNoTags,
        onClick = { screenModel.toggleNoTagsFilter() },
    )
    
    // Tag search input
    OutlinedTextField(
        value = tagSearchQuery,
        onValueChange = { screenModel.setTagSearchQuery(it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 8.dp),
        placeholder = { Text("Search tags...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = if (tagSearchQuery.isNotEmpty()) {
            {
                IconButton(onClick = { screenModel.setTagSearchQuery("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        } else null,
        singleLine = true,
    )

    if (tags.isEmpty() && !isLoading) {
        Text(
            text = "No tags found in library",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 8.dp),
        )
    } else if (isLoading && tags.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Loading tags...")
        }
    } else {
        Text(
            text = "Tap to include, tap again to exclude, tap again to clear",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 4.dp),
        )
        
        // Sort and filter tags
        val sortedTags = remember(tags, tagSortByName, tagSortAscending, tagSearchQuery, includedTags, excludedTags, tagCaseSensitive) {
            val filtered = if (tagSearchQuery.isBlank()) {
                tags
            } else {
                val query = if (tagCaseSensitive) tagSearchQuery else tagSearchQuery.lowercase()
                tags.filter { (tag, _) ->
                    val tagToMatch = if (tagCaseSensitive) tag else tag.lowercase()
                    tagToMatch.contains(query)
                }
            }
            
            // Sort with active tags prioritized
            val (activeTags, inactiveTags) = filtered.partition { (tag, _) ->
                tag in includedTags || tag in excludedTags
            }
            
            val sortComparator: Comparator<Pair<String, Int>> = if (tagSortByName) {
                if (tagSortAscending) {
                    compareBy { it.first.lowercase() }
                } else {
                    compareByDescending { it.first.lowercase() }
                }
            } else {
                if (tagSortAscending) {
                    compareBy { it.second }
                } else {
                    compareByDescending { it.second }
                }
            }
            
            activeTags.sortedWith(sortComparator) + inactiveTags.sortedWith(sortComparator)
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sortedTags.forEach { (tag, count) ->
                val isIncluded = tag in includedTags
                val isExcluded = tag in excludedTags

                FilterChip(
                    selected = isIncluded || isExcluded,
                    onClick = { screenModel.toggleTagIncluded(tag) },
                    label = { Text("$tag ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isExcluded) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        selectedLabelColor = if (isExcluded) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ExtensionsPage(
    screenModel: LibrarySettingsScreenModel,
) {
    val excludedExtensions by screenModel.libraryPreferences.excludedExtensions().collectAsState()
    val availableExtensions by screenModel.extensionsFlow.collectAsState()
    val isLoading by screenModel.isLoading.collectAsState()

    // Load data when first entering this page (only if empty)
    LaunchedEffect(Unit) {
        if (availableExtensions.isEmpty()) {
            screenModel.refreshExtensions()
        }
    }

    HeadingItem(MR.strings.label_extensions)

    // Refresh button
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = { screenModel.refreshExtensions(forceRefresh = true) }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            Spacer(Modifier.width(4.dp))
            Text("Refresh List")
        }
    }

    if (availableExtensions.isEmpty() && !isLoading) {
        Text(
            text = "No extensions with library entries",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
        )
    } else if (isLoading && availableExtensions.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Loading extensions...")
        }
    } else {
        // Check All / Uncheck All buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = { screenModel.checkAllExtensions() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Check All")
            }
            TextButton(
                onClick = { screenModel.uncheckAllExtensions() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Uncheck All")
            }
        }

        // Show count of missing sources
        val stubCount = availableExtensions.count { it.isStub }
        if (stubCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$stubCount source(s) with missing extensions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        availableExtensions.forEach { extensionInfo ->
            // Extension is checked if it's NOT in the excluded set
            val isChecked = extensionInfo.sourceId.toString() !in excludedExtensions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (extensionInfo.isStub) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Missing source",
                        modifier = Modifier
                            .padding(start = TabbedDialogPaddings.Horizontal)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                CheckboxItem(
                    label = if (extensionInfo.isStub) "${extensionInfo.sourceName} (Missing)" else extensionInfo.sourceName,
                    checked = isChecked,
                    onClick = {
                        screenModel.toggleExtensionFilter(extensionInfo.sourceId.toString(), !isChecked)
                    },
                )
            }
        }
    }
}
