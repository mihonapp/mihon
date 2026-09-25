package tachiyomi.domain.updates.model

import tachiyomi.domain.manga.model.Manga

data class MangaUpdateErrorWithManga(
    val error: MangaUpdateError,
    val manga: Manga,
)
