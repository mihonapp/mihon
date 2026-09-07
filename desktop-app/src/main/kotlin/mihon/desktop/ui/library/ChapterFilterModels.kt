package mihon.desktop.ui.library

enum class ChapterSortMode {
    SourceOrder,
    ChapterNumber,
    UploadDate,
}

data class ChapterSortState(
    val mode: ChapterSortMode = ChapterSortMode.SourceOrder,
    val ascending: Boolean = false,
)

data class ChapterFilterState(
    val unread: TriStateFilter = TriStateFilter.Disabled,
    val downloaded: TriStateFilter = TriStateFilter.Disabled,
    val bookmarked: TriStateFilter = TriStateFilter.Disabled,
) {
    val activeCount: Int
        get() = listOf(unread, downloaded, bookmarked)
            .count { it != TriStateFilter.Disabled }

    val hasActiveFilters: Boolean
        get() = activeCount > 0

    fun reset(): ChapterFilterState = ChapterFilterState()
}
