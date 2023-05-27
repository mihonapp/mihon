package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.components.TriStateItem
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import kotlinx.coroutines.flow.map
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.display
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.TriStateFilter
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SortItem

@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: LibrarySettingsScreenModel,
    category: Category,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(R.string.action_filter),
            stringResource(R.string.action_sort),
            stringResource(R.string.action_display),
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
                    category = category,
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
    TriStateItem(
        label = stringResource(R.string.label_downloaded),
        state = if (downloadedOnly) {
            TriStateFilter.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterDownloaded) },
    )
    val filterUnread by screenModel.libraryPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(R.string.action_filter_unread),
        state = filterUnread,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterUnread) },
    )
    val filterStarted by screenModel.libraryPreferences.filterStarted().collectAsState()
    TriStateItem(
        label = stringResource(R.string.label_started),
        state = filterStarted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterStarted) },
    )
    val filterBookmarked by screenModel.libraryPreferences.filterBookmarked().collectAsState()
    TriStateItem(
        label = stringResource(R.string.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterBookmarked) },
    )
    val filterCompleted by screenModel.libraryPreferences.filterCompleted().collectAsState()
    TriStateItem(
        label = stringResource(R.string.completed),
        state = filterCompleted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterCompleted) },
    )

    val trackServices = remember { screenModel.trackServices }
    when (trackServices.size) {
        0 -> {
            // No trackers
        }
        1 -> {
            val service = trackServices[0]
            val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
            TriStateItem(
                label = stringResource(R.string.action_filter_tracked),
                state = filterTracker,
                onClick = { screenModel.toggleTracker(service.id.toInt()) },
            )
        }
        else -> {
            HeadingItem(R.string.action_filter_tracked)
            trackServices.map { service ->
                val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
                TriStateItem(
                    label = stringResource(service.nameRes()),
                    state = filterTracker,
                    onClick = { screenModel.toggleTracker(service.id.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SortPage(
    category: Category,
    screenModel: LibrarySettingsScreenModel,
) {
    val sortingMode = category.sort.type
    val sortDescending = !category.sort.isAscending

    listOf(
        R.string.action_sort_alpha to LibrarySort.Type.Alphabetical,
        R.string.action_sort_total to LibrarySort.Type.TotalChapters,
        R.string.action_sort_last_read to LibrarySort.Type.LastRead,
        R.string.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
        R.string.action_sort_unread_count to LibrarySort.Type.UnreadCount,
        R.string.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
        R.string.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
        R.string.action_sort_date_added to LibrarySort.Type.DateAdded,
    ).map { (titleRes, mode) ->
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) LibrarySort.Direction.Ascending else LibrarySort.Direction.Descending
                    else -> if (sortDescending) LibrarySort.Direction.Descending else LibrarySort.Direction.Ascending
                }
                screenModel.setSort(category, mode, direction)
            },
        )
    }
}

@Composable
private fun ColumnScope.DisplayPage(
    category: Category,
    screenModel: LibrarySettingsScreenModel,
) {
    HeadingItem(R.string.action_display_mode)
    listOf(
        R.string.action_display_grid to LibraryDisplayMode.CompactGrid,
        R.string.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
        R.string.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
        R.string.action_display_list to LibraryDisplayMode.List,
    ).map { (titleRes, mode) ->
        RadioItem(
            label = stringResource(titleRes),
            selected = category.display == mode,
            onClick = { screenModel.setDisplayMode(category, mode) },
        )
    }

    if (category.display != LibraryDisplayMode.List) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = SettingsItemsPaddings.Vertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val configuration = LocalConfiguration.current
            val columnPreference = remember {
                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    screenModel.libraryPreferences.landscapeColumns()
                } else {
                    screenModel.libraryPreferences.portraitColumns()
                }
            }

            val columns by columnPreference.changes().collectAsState(initial = 0)
            Column(
                Modifier.weight(.5f),
            ) {
                Text(
                    stringResource(id = R.string.pref_library_columns),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (columns > 0) {
                    Text(stringResource(id = R.string.pref_library_columns_per_row, columns))
                } else {
                    Text(stringResource(id = R.string.label_default))
                }
            }

            Slider(
                value = columns.toFloat(),
                onValueChange = { columnPreference.set(it.toInt()) },
                modifier = Modifier.weight(1.5f),
                valueRange = 0f..10f,
                steps = 10,
            )
        }
    }

    HeadingItem(R.string.overlay_header)
    val downloadBadge by screenModel.libraryPreferences.downloadBadge().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.action_display_download_badge),
        checked = downloadBadge,
        onClick = {
            screenModel.togglePreference(LibraryPreferences::downloadBadge)
        },
    )
    val localBadge by screenModel.libraryPreferences.localBadge().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.action_display_local_badge),
        checked = localBadge,
        onClick = {
            screenModel.togglePreference(LibraryPreferences::localBadge)
        },
    )
    val languageBadge by screenModel.libraryPreferences.languageBadge().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.action_display_language_badge),
        checked = languageBadge,
        onClick = {
            screenModel.togglePreference(LibraryPreferences::languageBadge)
        },
    )
    val showContinueReadingButton by screenModel.libraryPreferences.showContinueReadingButton().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.action_display_show_continue_reading_button),
        checked = showContinueReadingButton,
        onClick = {
            screenModel.togglePreference(LibraryPreferences::showContinueReadingButton)
        },
    )

    HeadingItem(R.string.tabs_header)
    val categoryTabs by screenModel.libraryPreferences.categoryTabs().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.action_display_show_tabs),
        checked = categoryTabs,
        onClick = {
            screenModel.togglePreference(LibraryPreferences::categoryTabs)
        },
    )
    val categoryNumberOfItems by screenModel.libraryPreferences.categoryNumberOfItems().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.action_display_show_number_of_items),
        checked = categoryNumberOfItems,
        onClick = {
            screenModel.togglePreference(LibraryPreferences::categoryNumberOfItems)
        },
    )
}
