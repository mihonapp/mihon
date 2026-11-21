package eu.kanade.tachiyomi.data.track.suwayomi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MangaStatus(
    val rawValue: String,
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
data class MangaFragment(
    val artist: String?,
    val author: String?,
    val description: String?,
    val id: Int,
    val status: MangaStatus,
    val thumbnailUrl: String?,
    val title: String,
    val url: String,
    val genre: List<String>,
    val inLibraryAt: Long,
    val chapters: Chapters,
    val latestUploadedChapter: LatestUploadedChapter?,
    val latestFetchedChapter: LatestFetchedChapter?,
    val latestReadChapter: LatestReadChapter?,
    val unreadCount: Int,
    val downloadCount: Int,
) {
    @Serializable
    data class Chapters(
        val totalCount: Int,
    )

    @Serializable
    data class LatestUploadedChapter(
        val uploadDate: Long,
    )

    @Serializable
    data class LatestFetchedChapter(
        val fetchedAt: Long,
    )

    @Serializable
    data class LatestReadChapter(
        val lastReadAt: Long,
        val chapterNumber: Double,
    )
}

@Serializable
data class GetMangaResult(
    val data: GetMangaData,
)

@Serializable
data class GetMangaData(
    @SerialName("manga") val entry: MangaFragment,
)

@Serializable
data class GetMangaUnreadChaptersEntry(
    val nodes: List<GetMangaUnreadChaptersNode>,
)

@Serializable
data class GetMangaUnreadChaptersNode(
    val id: Int,
    val chapterNumber: Double,
)

@Serializable
data class GetMangaUnreadChaptersResult(
    val data: GetMangaUnreadChaptersData,
)

@Serializable
data class GetMangaUnreadChaptersData(
    @SerialName("chapters") val entry: GetMangaUnreadChaptersEntry,
)
