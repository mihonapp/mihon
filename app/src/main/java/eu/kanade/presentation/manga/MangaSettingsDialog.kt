package eu.kanade.presentation.manga

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.TriStateFilter
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.HorizontalPager
import eu.kanade.presentation.components.TabIndicator
import eu.kanade.presentation.components.rememberPagerState
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.launch

@Composable
fun ChapterSettingsDialog(
    onDismissRequest: () -> Unit,
    manga: Manga? = null,
    onDownloadFilterChanged: (TriStateFilter) -> Unit,
    onUnreadFilterChanged: (TriStateFilter) -> Unit,
    onBookmarkedFilterChanged: (TriStateFilter) -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onSetAsDefault: (applyToExistingManga: Boolean) -> Unit,
) {
    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) { contentPadding ->
        ChapterSettingsDialogImpl(
            manga = manga,
            contentPadding = contentPadding,
            onDownloadFilterChanged = onDownloadFilterChanged,
            onUnreadFilterChanged = onUnreadFilterChanged,
            onBookmarkedFilterChanged = onBookmarkedFilterChanged,
            onSortModeChanged = onSortModeChanged,
            onDisplayModeChanged = onDisplayModeChanged,
            onSetAsDefault = onSetAsDefault,
        )
    }
}

@Composable
private fun ChapterSettingsDialogImpl(
    manga: Manga? = null,
    contentPadding: PaddingValues = PaddingValues(),
    onDownloadFilterChanged: (TriStateFilter) -> Unit,
    onUnreadFilterChanged: (TriStateFilter) -> Unit,
    onBookmarkedFilterChanged: (TriStateFilter) -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onSetAsDefault: (applyToExistingManga: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tabTitles = listOf(
        stringResource(R.string.action_filter),
        stringResource(R.string.action_sort),
        stringResource(R.string.action_display),
    )
    val pagerState = rememberPagerState()

    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = onSetAsDefault,
        )
    }

    Column {
        Row {
            TabRow(
                modifier = Modifier.weight(1f),
                selectedTabIndex = pagerState.currentPage,
                indicator = { TabIndicator(it[pagerState.currentPage]) },
                divider = {},
            ) {
                tabTitles.fastForEachIndexed { i, s ->
                    val selected = pagerState.currentPage == i
                    Tab(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        text = {
                            Text(
                                text = s,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                }
            }

            MoreMenu(onSetAsDefault = { showSetAsDefaultDialog = true })
        }

        Divider()

        val density = LocalDensity.current
        var largestHeight by rememberSaveable { mutableStateOf(0f) }
        HorizontalPager(
            modifier = Modifier.heightIn(min = largestHeight.dp),
            count = tabTitles.size,
            state = pagerState,
            verticalAlignment = Alignment.Top,
        ) { page ->
            Box(
                modifier = Modifier.onSizeChanged {
                    with(density) {
                        val heightDp = it.height.toDp()
                        if (heightDp.value > largestHeight) {
                            largestHeight = heightDp.value
                        }
                    }
                },
            ) {
                when (page) {
                    0 -> {
                        val forceDownloaded = manga?.forceDownloaded() == true
                        FilterPage(
                            contentPadding = contentPadding,
                            downloadFilter = if (forceDownloaded) {
                                TriStateFilter.ENABLED_NOT
                            } else {
                                manga?.downloadedFilter
                            } ?: TriStateFilter.DISABLED,
                            onDownloadFilterChanged = onDownloadFilterChanged.takeUnless { forceDownloaded },
                            unreadFilter = manga?.unreadFilter ?: TriStateFilter.DISABLED,
                            onUnreadFilterChanged = onUnreadFilterChanged,
                            bookmarkedFilter = manga?.bookmarkedFilter ?: TriStateFilter.DISABLED,
                            onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                        )
                    }
                    1 -> SortPage(
                        contentPadding = contentPadding,
                        sortingMode = manga?.sorting ?: 0,
                        sortDescending = manga?.sortDescending() ?: false,
                        onItemSelected = onSortModeChanged,
                    )
                    2 -> DisplayPage(
                        contentPadding = contentPadding,
                        displayMode = manga?.displayMode ?: 0,
                        onItemSelected = onDisplayModeChanged,
                    )
                }
            }
        }
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
        title = { Text(text = stringResource(R.string.chapter_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.confirm_set_chapter_settings))

                Row(
                    modifier = Modifier
                        .clickable { optionalChecked = !optionalChecked }
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = optionalChecked,
                        onCheckedChange = null,
                    )
                    Text(text = stringResource(R.string.also_set_chapter_settings_for_library))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmed(optionalChecked)
                },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun MoreMenu(
    onSetAsDefault: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.label_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_chapter_settings_as_default)) },
                onClick = {
                    onSetAsDefault()
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun FilterPage(
    contentPadding: PaddingValues,
    downloadFilter: TriStateFilter,
    onDownloadFilterChanged: ((TriStateFilter) -> Unit)?,
    unreadFilter: TriStateFilter,
    onUnreadFilterChanged: (TriStateFilter) -> Unit,
    bookmarkedFilter: TriStateFilter,
    onBookmarkedFilterChanged: (TriStateFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(vertical = VerticalPadding)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        FilterPageItem(
            label = stringResource(R.string.action_filter_downloaded),
            state = downloadFilter,
            onClick = onDownloadFilterChanged,
        )
        FilterPageItem(
            label = stringResource(R.string.action_filter_unread),
            state = unreadFilter,
            onClick = onUnreadFilterChanged,
        )
        FilterPageItem(
            label = stringResource(R.string.action_filter_bookmarked),
            state = bookmarkedFilter,
            onClick = onBookmarkedFilterChanged,
        )
    }
}

@Composable
private fun FilterPageItem(
    label: String,
    state: TriStateFilter,
    onClick: ((TriStateFilter) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clickable(
                enabled = onClick != null,
                onClick = {
                    when (state) {
                        TriStateFilter.DISABLED -> onClick?.invoke(TriStateFilter.ENABLED_IS)
                        TriStateFilter.ENABLED_IS -> onClick?.invoke(TriStateFilter.ENABLED_NOT)
                        TriStateFilter.ENABLED_NOT -> onClick?.invoke(TriStateFilter.DISABLED)
                    }
                },
            )
            .fillMaxWidth()
            .padding(horizontal = HorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = when (state) {
                TriStateFilter.DISABLED -> Icons.Rounded.CheckBoxOutlineBlank
                TriStateFilter.ENABLED_IS -> Icons.Rounded.CheckBox
                TriStateFilter.ENABLED_NOT -> Icons.Rounded.DisabledByDefault
            },
            contentDescription = null,
            tint = if (state == TriStateFilter.DISABLED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SortPage(
    contentPadding: PaddingValues,
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(contentPadding)
            .padding(vertical = VerticalPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        val arrowIcon = if (sortDescending) {
            Icons.Default.ArrowDownward
        } else {
            Icons.Default.ArrowUpward
        }

        SortPageItem(
            label = stringResource(R.string.sort_by_source),
            statusIcon = arrowIcon.takeIf { sortingMode == Manga.CHAPTER_SORTING_SOURCE },
            onClick = { onItemSelected(Manga.CHAPTER_SORTING_SOURCE) },
        )
        SortPageItem(
            label = stringResource(R.string.sort_by_number),
            statusIcon = arrowIcon.takeIf { sortingMode == Manga.CHAPTER_SORTING_NUMBER },
            onClick = { onItemSelected(Manga.CHAPTER_SORTING_NUMBER) },
        )
        SortPageItem(
            label = stringResource(R.string.sort_by_upload_date),
            statusIcon = arrowIcon.takeIf { sortingMode == Manga.CHAPTER_SORTING_UPLOAD_DATE },
            onClick = { onItemSelected(Manga.CHAPTER_SORTING_UPLOAD_DATE) },
        )
    }
}

@Composable
private fun SortPageItem(
    label: String,
    statusIcon: ImageVector?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = HorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (statusIcon != null) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DisplayPage(
    contentPadding: PaddingValues,
    displayMode: Long,
    onItemSelected: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(contentPadding)
            .padding(vertical = VerticalPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        DisplayPageItem(
            label = stringResource(R.string.show_title),
            selected = displayMode == Manga.CHAPTER_DISPLAY_NAME,
            onClick = { onItemSelected(Manga.CHAPTER_DISPLAY_NAME) },
        )
        DisplayPageItem(
            label = stringResource(R.string.show_chapter_number),
            selected = displayMode == Manga.CHAPTER_DISPLAY_NUMBER,
            onClick = { onItemSelected(Manga.CHAPTER_DISPLAY_NUMBER) },
        )
    }
}

@Composable
private fun DisplayPageItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = HorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val HorizontalPadding = 24.dp
private val VerticalPadding = 8.dp

@Preview(
    name = "Light",
)
@Preview(
    name = "Dark",
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun ChapterSettingsDialogPreview() {
    TachiyomiTheme {
        Surface {
            ChapterSettingsDialogImpl(
                onDownloadFilterChanged = {},
                onUnreadFilterChanged = {},
                onBookmarkedFilterChanged = {},
                onSortModeChanged = {},
                onDisplayModeChanged = {},
            ) {}
        }
    }
}
