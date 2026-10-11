package tachiyomi.domain.history.model

import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    val title: String,
    val chapterNumber: Double,
    val readAt: Instant?,
    val readDuration: Long,
    val coverData: MangaCover,
)
