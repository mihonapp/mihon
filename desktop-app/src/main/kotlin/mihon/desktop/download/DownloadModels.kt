package mihon.desktop.download

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
}

@Serializable
enum class PageStatus {
    QUEUE,
    DOWNLOADING,
    READY,
    ERROR,
}

@Serializable
data class DownloadPage(
    val index: Int,
    val url: String,
    val imageUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val status: PageStatus = PageStatus.QUEUE,
    val progress: Float = 0f,
    val bytesWritten: Long = 0L,
    val error: String? = null,
)

@Serializable
data class DesktopDownload(
    val chapterId: Long,
    val mangaId: Long,
    val sourceId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val chapterUrl: String,
    val pages: List<DownloadPage> = emptyList(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val error: String? = null,
    val bytesDownloaded: Long = 0L,
    val enqueuedAt: Long = System.currentTimeMillis(),
) {
    val downloadedImages: Int
        get() = pages.count { it.status == PageStatus.READY }

    val totalPages: Int
        get() = pages.size
}
