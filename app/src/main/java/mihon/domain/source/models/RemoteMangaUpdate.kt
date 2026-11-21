package mihon.domain.source.models

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

data class RemoteMangaUpdate(
    val manga: Manga,
    val newChapters: List<Chapter>,
)
