package eu.kanade.tachiyomi.data.track.suwayomi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public enum class MangaStatus(
    public val rawValue: String,
) {
    UNKNOWN("UNKNOWN"),
    ONGOING("ONGOING"),
    COMPLETED("COMPLETED"),
    LICENSED("LICENSED"),
    PUBLISHING_FINISHED("PUBLISHING_FINISHED"),
    CANCELLED("CANCELLED"),
    ON_HIATUS("ON_HIATUS"),
}

@Serializable
public data class MangaFragment(
    public val artist: String?,
    public val author: String?,
    public val description: String?,
    public val id: Int,
    public val status: MangaStatus,
    public val thumbnailUrl: String?,
    public val title: String,
    public val url: String,
    public val genre: List<String>,
    public val inLibraryAt: Long,
    public val chapters: Chapters,
    public val latestUploadedChapter: LatestUploadedChapter?,
    public val latestFetchedChapter: LatestFetchedChapter?,
    public val latestReadChapter: LatestReadChapter?,
    public val unreadCount: Int,
    public val downloadCount: Int,
) {
    @Serializable
    public data class Chapters(
        public val totalCount: Int,
    )

    @Serializable
    public data class LatestUploadedChapter(
        public val uploadDate: Long,
    )

    @Serializable
    public data class LatestFetchedChapter(
        public val fetchedAt: Long,
    )

    @Serializable
    public data class LatestReadChapter(
        public val lastReadAt: Long,
        public val chapterNumber: Double,
    )
}

@Serializable
public data class GetMangaResult(
    public val data: GetMangaData,
)

@Serializable
public data class GetMangaData(
    @SerialName("manga")
    public val entry: MangaFragment,
)

@Serializable
public data class GetMangaUnreadChaptersEntry(
    public val nodes: List<GetMangaUnreadChaptersNode>,
)

@Serializable
public data class GetMangaUnreadChaptersNode(
    public val id: Int,
    public val chapterNumber: Double,
)

@Serializable
public data class GetMangaUnreadChaptersResult(
    public val data: GetMangaUnreadChaptersData,
)

@Serializable
public data class GetMangaUnreadChaptersData(
    @SerialName("chapters")
    public val entry: GetMangaUnreadChaptersEntry,
)
