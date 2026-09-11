package mihon.desktop.library.update

import mihon.desktop.library.model.LibraryChapter

data class LibraryUpdateOptions(
    val skipCompleted: Boolean = true,
    val skipUnread: Boolean = false,
    val skipNotStarted: Boolean = false,
    val includedCategoryIds: Set<Long>? = null,
    val excludedCategoryIds: Set<Long>? = null,
    val autoDownloadNewChapters: Boolean = false,
)

data class MangaUpdateItemResult(
    val mangaId: Long,
    val title: String,
    val newChapters: List<LibraryChapter>,
    val error: String? = null,
)

data class LibraryUpdateReport(
    val totalMangaChecked: Int,
    val updatedMangaCount: Int,
    val newChaptersTotal: Int,
    val results: List<MangaUpdateItemResult>,
    val errors: List<String>,
)

data class LibraryUpdateProgress(
    val currentMangaTitle: String,
    val currentIndex: Int,
    val totalManga: Int,
)
