package tachiyomi.domain.manga.model

data class MangaWithChapterCount(
    val manga: Manga,
    val chapterCount: Long,
)
