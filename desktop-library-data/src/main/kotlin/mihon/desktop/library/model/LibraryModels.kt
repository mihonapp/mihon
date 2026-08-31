package mihon.desktop.library.model

data class LibraryManga(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val chapterCount: Long,
    val unreadCount: Long,
)

data class MangaDetails(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genreJson: String,
    val status: Long,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val updateStrategy: String,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val excludedScanlatorsJson: String,
    val version: Long,
    val notes: String,
    val initialized: Boolean,
    val memoJson: String,
    val categories: List<CategoryRecord>,
)

data class LibraryChapter(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val dateUpload: Long,
    val chapterNumber: Double,
    val sourceOrder: Long,
    val lastModifiedAt: Long,
    val version: Long,
    val memoJson: String,
)

data class MangaRecord(
    val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genreJson: String = "[]",
    val status: Long = 0,
    val thumbnailUrl: String? = null,
    val favorite: Boolean = true,
    val dateAdded: Long = 0,
    val viewerFlags: Long = 0,
    val chapterFlags: Long = 0,
    val updateStrategy: String = "ALWAYS_UPDATE",
    val lastModifiedAt: Long = 0,
    val favoriteModifiedAt: Long? = null,
    val excludedScanlatorsJson: String = "[]",
    val version: Long = 0,
    val notes: String = "",
    val initialized: Boolean = false,
    val memoJson: String = "{}",
)

data class ChapterRecord(
    val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Long = 0,
    val dateFetch: Long = 0,
    val dateUpload: Long = 0,
    val chapterNumber: Double = 0.0,
    val sourceOrder: Long = 0,
    val lastModifiedAt: Long = 0,
    val version: Long = 0,
    val memoJson: String = "{}",
)

data class CategoryRecord(
    val id: Long = 0,
    val name: String,
    val sortOrder: Long = 0,
    val flags: Long = 0,
)

data class HistoryRecord(
    val chapterId: Long,
    val lastRead: Long,
    val readDuration: Long = 0,
)

data class TrackingRecord(
    val id: Long = 0,
    val mangaId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val libraryId: Long = 0,
    val title: String = "",
    val lastChapterRead: Double = 0.0,
    val totalChapters: Long = 0,
    val score: Double = 0.0,
    val status: Long = 0,
    val startedReadingDate: Long = 0,
    val finishedReadingDate: Long = 0,
    val private: Boolean = false,
    val trackingUrl: String = "",
)

data class SourceRecord(
    val sourceId: Long,
    val name: String,
    val importedAt: Long,
)

data class PreferenceSnapshotRecord(
    val key: String,
    val valueType: String,
    val valueJson: String,
    val importedAt: Long,
)

data class SourcePreferenceSnapshotRecord(
    val sourceKey: String,
    val key: String,
    val valueType: String,
    val valueJson: String,
    val importedAt: Long,
)

data class LocalMangaRecord(
    val mangaId: Long,
    val storagePath: String,
    val manifestSha256: String,
    val importedAt: Long,
)

data class LocalChapterRecord(
    val chapterId: Long,
    val relativePath: String,
    val assetKind: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)
