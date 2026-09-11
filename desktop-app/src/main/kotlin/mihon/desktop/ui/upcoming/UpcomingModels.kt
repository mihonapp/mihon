package mihon.desktop.ui.upcoming

import mihon.desktop.category.DesktopCategory
import mihon.desktop.ui.library.TriStateFilter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One chapter with a release date in the future.
 *
 * [sourceName] is resolved from the desktop source table when available. Older libraries that
 * have never imported an Android backup do not have source rows, so the UI falls back to
 * [sourceLabel] and renders "Source #id".
 */
data class UpcomingChapterEntry(
    val mangaId: Long,
    val mangaTitle: String,
    val mangaThumbnailUrl: String?,
    val sourceId: Long,
    val sourceName: String?,
    val chapterId: Long,
    val chapterName: String,
    val chapterNumber: Double,
    val dateUpload: Long,
    val categoryIds: Set<Long> = emptySet(),
) {
    val sourceLabel: String
        get() = sourceName?.takeIf { it.isNotBlank() } ?: "Source $sourceId"

    fun dateIn(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(dateUpload).atZone(zoneId).toLocalDate()
}

data class UpcomingDay(
    val date: LocalDate,
    val chapters: List<UpcomingChapterEntry>,
)

/**
 * Immutable UI state for the Upcoming calendar.
 *
 * [entriesByDate] always contains only chapters whose `dateUpload` is in the future. Category
 * filters are applied before grouping, so both [monthDays] and [visibleEntries] reflect the
 * currently active include/exclude selection.
 */
data class UpcomingUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val today: LocalDate = LocalDate.now(),
    val entriesByDate: Map<LocalDate, List<UpcomingChapterEntry>> = emptyMap(),
    val hasAnyUpcomingBeforeFilters: Boolean = false,
    val categories: List<DesktopCategory> = emptyList(),
    val categoryFilters: Map<Long, TriStateFilter> = emptyMap(),
    val isFilterDialogOpen: Boolean = false,
) {
    /** Event counts for day cells of [selectedMonth]. */
    val monthDays: Map<LocalDate, Int>
        get() = entriesByDate
            .filterKeys { YearMonth.from(it) == selectedMonth }
            .mapValues { (_, entries) -> entries.size }

    /** Days of [selectedMonth] that contain upcoming chapters, ordered by date. */
    val days: List<UpcomingDay>
        get() = monthDays.keys
            .sorted()
            .map { date -> UpcomingDay(date, entriesByDate[date].orEmpty()) }

    /** All chapters in [selectedMonth] (filtered), ordered by release date. */
    val monthEntries: List<UpcomingChapterEntry>
        get() = days.flatMap { it.chapters }

    val selectedDay: UpcomingDay?
        get() = selectedDate?.let { date -> UpcomingDay(date, entriesByDate[date].orEmpty()) }

    /** Entries for the selected day, or the whole selected month when no day is selected. */
    val visibleEntries: List<UpcomingChapterEntry>
        get() = selectedDate?.let { entriesByDate[it].orEmpty() } ?: monthEntries

    val hasActiveFilters: Boolean
        get() = categoryFilters.values.any { it != TriStateFilter.Disabled }

    val hasAnyUpcoming: Boolean
        get() = entriesByDate.isNotEmpty()

    val isEmpty: Boolean
        get() = visibleEntries.isEmpty()

    val emptyTitle: String
        get() = when {
            !hasAnyUpcomingBeforeFilters -> "No upcoming chapters"
            hasActiveFilters && visibleEntries.isEmpty() -> "No matching upcoming chapters"
            selectedDate != null -> "No chapters on this day"
            else -> "No upcoming chapters this month"
        }

    val emptySubtitle: String
        get() = when {
            !hasAnyUpcomingBeforeFilters -> "Future chapter release dates from your library will appear here."
            hasActiveFilters -> "Try clearing or changing the category filters."
            selectedDate != null -> "Pick another day or show the whole month."
            else -> "Browse another month to see upcoming chapters."
        }
}
