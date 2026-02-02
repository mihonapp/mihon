package tachiyomi.domain.manga.model

data class MangaWithChapterCount(
    val manga: Manga,
    val chapterCount: Long,
    val downloadedCount: Long = 0,
    val readCount: Long = 0,
)
