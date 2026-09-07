package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings

@Composable
fun LibraryFilterDialog(
    filterState: LibraryFilterState,
    sortState: LibrarySortState,
    onFilterChange: (LibraryFilterState) -> Unit,
    onSortChange: (LibrarySortState) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.libraryFilterAndSort,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().testTag("library-filter-dialog")) {
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            val badge = if (filterState.hasActiveFilters) " (${filterState.activeCount})" else ""
                            Text("${strings.libraryFilterTab}$badge")
                        },
                        modifier = Modifier.testTag("filter-tab-filters"),
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.librarySortTab) },
                        modifier = Modifier.testTag("filter-tab-sort"),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Filter Tab
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Click to cycle: Off -> Include (√) -> Exclude (✕)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )

                        TriStateChipRow(
                            label = strings.libraryFilterUnread,
                            state = filterState.unread,
                            onClick = { onFilterChange(filterState.copy(unread = filterState.unread.next())) },
                            testTag = "filter-unread",
                        )
                        TriStateChipRow(
                            label = strings.libraryFilterDownloaded,
                            state = filterState.downloaded,
                            onClick = { onFilterChange(filterState.copy(downloaded = filterState.downloaded.next())) },
                            testTag = "filter-downloaded",
                        )
                        TriStateChipRow(
                            label = strings.libraryFilterStarted,
                            state = filterState.started,
                            onClick = { onFilterChange(filterState.copy(started = filterState.started.next())) },
                            testTag = "filter-started",
                        )
                        TriStateChipRow(
                            label = strings.libraryFilterCompleted,
                            state = filterState.completed,
                            onClick = { onFilterChange(filterState.copy(completed = filterState.completed.next())) },
                            testTag = "filter-completed",
                        )
                        TriStateChipRow(
                            label = strings.libraryFilterBookmarked,
                            state = filterState.bookmarked,
                            onClick = { onFilterChange(filterState.copy(bookmarked = filterState.bookmarked.next())) },
                            testTag = "filter-bookmarked",
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { onFilterChange(filterState.reset()) },
                            enabled = filterState.hasActiveFilters,
                            modifier = Modifier.align(Alignment.End).testTag("filter-reset-button"),
                        ) {
                            Text(strings.libraryFilterReset)
                        }
                    }
                } else {
                    // Sort Tab
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val sortModes = listOf(
                            LibrarySortMode.None to strings.librarySortDefault,
                            LibrarySortMode.Alphabetical to strings.librarySortAlphabetical,
                            LibrarySortMode.LastRead to strings.librarySortLastRead,
                            LibrarySortMode.LastUpdate to strings.librarySortLastUpdate,
                            LibrarySortMode.UnreadCount to strings.librarySortUnreadCount,
                            LibrarySortMode.TotalChapters to strings.librarySortTotalChapters,
                            LibrarySortMode.DateAdded to strings.librarySortDateAdded,
                        )

                        for ((mode, label) in sortModes) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSortChange(sortState.copy(mode = mode)) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = sortState.mode == mode,
                                    onClick = { onSortChange(sortState.copy(mode = mode)) },
                                    modifier = Modifier.testTag("sort-option-${mode.name}"),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = sortState.ascending,
                                onClick = { onSortChange(sortState.copy(ascending = true)) },
                                label = { Text(strings.librarySortAscending) },
                                modifier = Modifier.weight(1f).testTag("sort-ascending"),
                            )
                            FilterChip(
                                selected = !sortState.ascending,
                                onClick = { onSortChange(sortState.copy(ascending = false)) },
                                label = { Text(strings.librarySortDescending) },
                                modifier = Modifier.weight(1f).testTag("sort-descending"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("filter-dialog-done")) {
                Text(strings.dialogDone)
            }
        },
    )
}

@Composable
private fun TriStateChipRow(
    label: String,
    state: TriStateFilter,
    onClick: () -> Unit,
    testTag: String,
) {
    val (bgColor, textColor, symbol) = when (state) {
        TriStateFilter.Disabled -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "—",
        )
        TriStateFilter.Include -> Triple(
            Color(0xFF2E7D32), // Green
            Color.White,
            "✓",
        )
        TriStateFilter.Exclude -> Triple(
            Color(0xFFC62828), // Red
            Color.White,
            "✕",
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
    }
}
