package eu.kanade.presentation.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.forceDownloaded
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.preference.TriState
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun ChapterSettingsDialog(
    onDismissRequest: () -> Unit,
    manga: Manga? = null,
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnreadFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    scanlatorFilterActive: Boolean,
    onScanlatorFilterClicked: (() -> Unit),
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onSetAsDefault: (applyToExistingManga: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
) {
    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = onSetAsDefault,
        )
    }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
        tabOverflowMenuContent = { closeMenu ->
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.set_chapter_settings_as_default)) },
                onClick = {
                    showSetAsDefaultDialog = true
                    closeMenu()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_reset)) },
                onClick = {
                    onResetToDefault()
                    closeMenu()
                },
            )
        },
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    FilterPage(
                        downloadFilter = manga?.downloadedFilter ?: TriState.DISABLED,
                        onDownloadFilterChanged = onDownloadFilterChanged
                            .takeUnless { manga?.forceDownloaded() == true },
                        unreadFilter = manga?.unreadFilter ?: TriState.DISABLED,
                        onUnreadFilterChanged = onUnreadFilterChanged,
                        bookmarkedFilter = manga?.bookmarkedFilter ?: TriState.DISABLED,
                        onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                        scanlatorFilterActive = scanlatorFilterActive,
                        onScanlatorFilterClicked = onScanlatorFilterClicked,
                    )
                }
                1 -> {
                    SortPage(
                        sortingMode = manga?.sorting ?: 0,
                        sortDescending = manga?.sortDescending() ?: false,
                        onItemSelected = onSortModeChanged,
                    )
                }
                2 -> {
                    DisplayPage(
                        displayMode = manga?.displayMode ?: 0,
                        onItemSelected = onDisplayModeChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    downloadFilter: TriState,
    onDownloadFilterChanged: ((TriState) -> Unit)?,
    unreadFilter: TriState,
    onUnreadFilterChanged: (TriState) -> Unit,
    bookmarkedFilter: TriState,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    scanlatorFilterActive: Boolean,
    onScanlatorFilterClicked: (() -> Unit),
) {
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = downloadFilter,
        onClick = onDownloadFilterChanged,
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = unreadFilter,
        onClick = onUnreadFilterChanged,
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = bookmarkedFilter,
        onClick = onBookmarkedFilterChanged,
    )
    ScanlatorFilterItem(
        active = scanlatorFilterActive,
        onClick = onScanlatorFilterClicked,
    )
}

@Composable
fun ScanlatorFilterItem(
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PeopleAlt,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.active
            } else {
                LocalContentColor.current
            },
        )
        Text(
            text = stringResource(MR.strings.scanlator),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ColumnScope.SortPage(
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.sort_by_source to Manga.CHAPTER_SORTING_SOURCE,
        MR.strings.sort_by_number to Manga.CHAPTER_SORTING_NUMBER,
        MR.strings.sort_by_upload_date to Manga.CHAPTER_SORTING_UPLOAD_DATE,
        MR.strings.action_sort_alpha to Manga.CHAPTER_SORTING_ALPHABET,
    ).map { (titleRes, mode) ->
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = { onItemSelected(mode) },
        )
    }
}

@Composable
private fun ColumnScope.DisplayPage(
    displayMode: Long,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.show_title to Manga.CHAPTER_DISPLAY_NAME,
        MR.strings.show_chapter_number to Manga.CHAPTER_DISPLAY_NUMBER,
    ).map { (titleRes, mode) ->
        RadioItem(
            label = stringResource(titleRes),
            selected = displayMode == mode,
            onClick = { onItemSelected(mode) },
        )
    }
}

@Composable
private fun SetAsDefaultDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (optionalChecked: Boolean) -> Unit,
) {
    var optionalChecked by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.chapter_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(MR.strings.confirm_set_chapter_settings))

                LabeledCheckbox(
                    label = stringResource(MR.strings.also_set_chapter_settings_for_library),
                    checked = optionalChecked,
                    onCheckedChange = { optionalChecked = it },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmed(optionalChecked)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
