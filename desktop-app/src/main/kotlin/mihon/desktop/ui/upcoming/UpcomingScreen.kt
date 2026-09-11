package mihon.desktop.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.ui.library.TriStateFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val UPCOMING_SCREEN_TEST_TAG = "upcoming_screen"
const val UPCOMING_BACK_BUTTON_TEST_TAG = "upcoming_back"
const val UPCOMING_FILTER_BUTTON_TEST_TAG = "upcoming_filter_button"
const val UPCOMING_CLEAR_FILTERS_TEST_TAG = "upcoming_clear_filters"
const val UPCOMING_EMPTY_CLEAR_FILTERS_TEST_TAG = "upcoming_empty_clear_filters"
const val UPCOMING_EMPTY_STATE_TEST_TAG = "upcoming_empty_state"
const val UPCOMING_ERROR_STATE_TEST_TAG = "upcoming_error_state"
const val UPCOMING_DAY_HEADER_TEST_TAG = "upcoming_day_header"
const val UPCOMING_SHOW_MONTH_TEST_TAG = "upcoming_show_month"
const val UPCOMING_LIST_HEADER_TEST_TAG = "upcoming_list_header"

@Composable
fun UpcomingScreen(
    state: UpcomingUiState = UpcomingUiState(loading = false),
    onBack: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectDate: (LocalDate?) -> Unit = {},
    onOpenFilter: () -> Unit = {},
    onDismissFilter: () -> Unit = {},
    onCycleCategory: (Long) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onOpenManga: (Long) -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag(UPCOMING_SCREEN_TEST_TAG),
    ) {
        UpcomingToolbar(
            state = state,
            onBack = onBack,
            onOpenFilter = onOpenFilter,
            onClearFilters = onClearFilters,
        )
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                UpcomingCalendar(
                    selectedMonth = state.selectedMonth,
                    selectedDate = state.selectedDate,
                    today = state.today,
                    eventCounts = state.monthDays,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = { onSelectDate(it) },
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                UpcomingListHeader(
                    state = state,
                    onSelectDate = onSelectDate,
                )
                Spacer(modifier = Modifier.size(8.dp))
                when {
                    state.loading -> UpcomingLoading()
                    state.errorMessage != null -> UpcomingError(state.errorMessage, onRetry)
                    state.visibleEntries.isEmpty() -> UpcomingEmptyState(state, onClearFilters)
                    else -> UpcomingEntryList(state, onOpenManga)
                }
            }
        }
    }

    if (state.isFilterDialogOpen) {
        UpcomingFilterDialog(
            categories = state.categories,
            filters = state.categoryFilters,
            onCycleCategory = onCycleCategory,
            onClearFilters = onClearFilters,
            onDismiss = onDismissFilter,
        )
    }
}

@Composable
private fun UpcomingToolbar(
    state: UpcomingUiState,
    onBack: () -> Unit,
    onOpenFilter: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag(UPCOMING_BACK_BUTTON_TEST_TAG),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Upcoming",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasActiveFilters) {
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.testTag(UPCOMING_CLEAR_FILTERS_TEST_TAG),
                ) {
                    Text("Clear filters")
                }
            }
            FilledTonalButton(
                onClick = onOpenFilter,
                modifier = Modifier.testTag(UPCOMING_FILTER_BUTTON_TEST_TAG),
            ) {
                Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                val activeCount = state.categoryFilters.values.count { it != TriStateFilter.Disabled }
                Text(if (activeCount > 0) "Filters ($activeCount)" else "Filters")
            }
        }
    }
}

@Composable
private fun UpcomingListHeader(
    state: UpcomingUiState,
    onSelectDate: (LocalDate?) -> Unit,
) {
    val locale = Locale.getDefault()
    if (state.selectedDate != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = formatUpcomingDayHeading(state.selectedDate, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag(UPCOMING_DAY_HEADER_TEST_TAG),
                )
                Text(
                    text = "${state.visibleEntries.size} chapter${if (state.visibleEntries.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { onSelectDate(null) },
                modifier = Modifier.testTag(UPCOMING_SHOW_MONTH_TEST_TAG),
            ) {
                Text("Show month")
            }
        }
    } else {
        Text(
            text = state.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(UPCOMING_LIST_HEADER_TEST_TAG),
        )
    }
}

@Composable
private fun UpcomingLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UpcomingError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(UPCOMING_ERROR_STATE_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun UpcomingEmptyState(
    state: UpcomingUiState,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(UPCOMING_EMPTY_STATE_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = state.emptyTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = state.emptySubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.hasActiveFilters) {
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.testTag(UPCOMING_EMPTY_CLEAR_FILTERS_TEST_TAG),
            ) {
                Text("Clear filters")
            }
        }
    }
}

@Composable
private fun UpcomingEntryList(
    state: UpcomingUiState,
    onOpenManga: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (state.selectedDate == null) {
            state.days.forEach { day ->
                item(key = "upcoming-day-${day.date}") {
                    UpcomingDayHeading(day)
                }
                items(day.chapters, key = { "upcoming-chapter-${it.chapterId}" }) { entry ->
                    UpcomingItem(
                        entry = entry,
                        onClick = { onOpenManga(entry.mangaId) },
                    )
                }
            }
        } else {
            items(state.visibleEntries, key = { "upcoming-chapter-${it.chapterId}" }) { entry ->
                UpcomingItem(
                    entry = entry,
                    onClick = { onOpenManga(entry.mangaId) },
                )
            }
        }
    }
}

@Composable
private fun UpcomingDayHeading(day: UpcomingDay) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatUpcomingDayHeading(day.date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(day.chapters.size.toString())
        }
    }
}
