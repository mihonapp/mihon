package eu.kanade.domain.history.model

import java.util.Date

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    val title: String,
    val thumbnailUrl: String,
    val chapterNumber: Float,
    val readAt: Date?,
    val readDuration: Long,
)
