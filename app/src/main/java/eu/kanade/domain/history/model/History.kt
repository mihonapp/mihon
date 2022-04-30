package eu.kanade.domain.history.model

import java.util.Date

data class History(
    val id: Long?,
    val chapterId: Long,
    val readAt: Date?
)
