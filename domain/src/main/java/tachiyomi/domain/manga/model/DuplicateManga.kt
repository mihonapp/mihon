package tachiyomi.domain.manga.model

class DuplicateManga(
    val sourceMangaId: Long,
    val manga: Manga,
    val chapterCount: Long,
)
