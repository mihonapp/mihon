package eu.kanade.domain.history.model

import eu.kanade.domain.manga.model.MangaCover
import java.util.Date

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    val title: String,
    val chapterNumber: Float,
    val readAt: Date?,
    val readDuration: Long,
    val coverData: MangaCover,
)
