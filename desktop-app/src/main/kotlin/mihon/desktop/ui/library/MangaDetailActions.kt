package mihon.desktop.ui.library

/**
 * The complete mutation surface shared by library and source-browse manga details.
 * Navigation, source refresh, and library membership remain entry-specific concerns.
 */
data class MangaDetailActions(
    val onReadChapter: (Long) -> Unit = {},
    val onEditCategories: () -> Unit = {},
    val onOpenTracking: () -> Unit = {},
    val onEditInfo: () -> Unit = {},
    val onDismissEditInfo: () -> Unit = {},
    val onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    val onResetMangaInfo: () -> Unit = {},
    val onChapterFilterChange: (ChapterFilterState) -> Unit = {},
    val onChapterSortChange: (ChapterSortState) -> Unit = {},
    val onToggleBookmark: (Long) -> Unit = {},
    val onToggleRead: (Long) -> Unit = {},
    val onMarkPreviousRead: (Long) -> Unit = {},
    val onDownloadChapter: (Long) -> Unit = {},
    val onDeleteDownload: (Long) -> Unit = {},
    val onDownloadBatch: (Int?) -> Unit = {},
    val onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit = { _, _ -> },
    val onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = { _, _ -> },
    val onBatchDownloadChapters: (Set<Long>) -> Unit = {},
    val onBatchDeleteDownloads: (Set<Long>) -> Unit = {},
    val onOpenChapterSettings: () -> Unit = {},
    val onDismissChapterSettings: () -> Unit = {},
    val onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit = {},
    val onExcludedScanlatorsChange: (Set<String>) -> Unit = {},
    val onShowMissingChaptersChange: (Boolean) -> Unit = {},
    val onSetChapterSettingsAsDefault: (Boolean) -> Unit = {},
    val onResetChapterSettingsToDefault: () -> Unit = {},
)
