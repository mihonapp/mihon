package mihon.desktop.ui.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class LibraryDisplayMode {
    ComfortableGrid,
    CompactGrid,
    CoverOnly,
    List,
}

enum class LibrarySortMode {
    None,
    Alphabetical,
    LastRead,
    LastUpdate,
    UnreadCount,
    TotalChapters,
    DateAdded,
}

enum class TriStateFilter {
    Disabled,
    Include,
    Exclude,
    ;

    fun next(): TriStateFilter = when (this) {
        Disabled -> Include
        Include -> Exclude
        Exclude -> Disabled
    }
}

data class LibraryFilterState(
    val unread: TriStateFilter = TriStateFilter.Disabled,
    val downloaded: TriStateFilter = TriStateFilter.Disabled,
    val started: TriStateFilter = TriStateFilter.Disabled,
    val completed: TriStateFilter = TriStateFilter.Disabled,
    val bookmarked: TriStateFilter = TriStateFilter.Disabled,
) {
    val activeCount: Int
        get() = listOf(unread, downloaded, started, completed, bookmarked)
            .count { it != TriStateFilter.Disabled }

    val hasActiveFilters: Boolean
        get() = activeCount > 0

    fun reset(): LibraryFilterState = LibraryFilterState()
}

data class LibrarySortState(
    val mode: LibrarySortMode = LibrarySortMode.None,
    val ascending: Boolean = true,
)

data class LibrarySelectionState(
    val isSelectionMode: Boolean = false,
    val selectedMangaIds: Set<Long> = emptySet(),
) {
    val count: Int get() = selectedMangaIds.size
    val isAnySelected: Boolean get() = selectedMangaIds.isNotEmpty()
}

data class LibraryDisplaySettings(
    val mode: LibraryDisplayMode = LibraryDisplayMode.ComfortableGrid,
    val gridSizeDp: Dp = 180.dp,
)
