package tachiyomi.domain.manga.model

data class DuplicateManga(
    val manga: Manga,
    val totalChapters: Long,
)
