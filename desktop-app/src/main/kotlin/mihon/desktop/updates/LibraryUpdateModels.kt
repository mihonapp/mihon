package mihon.desktop.updates

data class UpdatedChapterItem(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val chapterNumber: Double,
    val dateFetch: Long,
    val mangaThumbnailUrl: String? = null,
)

data class LibraryUpdateResult(
    val totalMangaChecked: Int,
    val mangaWithNewChapters: Int,
    val newChaptersFound: Int,
    val updatedMangaTitles: List<String>,
    val errors: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)
