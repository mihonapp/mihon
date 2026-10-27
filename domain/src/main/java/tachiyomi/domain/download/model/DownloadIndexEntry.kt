package tachiyomi.domain.download.model

data class DownloadIndexEntry(
    val sourceId: Long,
    val sourceDir: String,
    val mangaDir: String,
    val chapterDir: String,
)
