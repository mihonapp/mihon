package tachiyomi.domain.history.model

import tachiyomi.domain.manga.model.MangaCover

data class ReadDurationByManga(
    val mangaId: Long,
    val title: String,
    val totalTimeRead: Long,
    val cover: MangaCover,
)
