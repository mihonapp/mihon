package tachiyomi.domain.history.model

import kotlin.time.Instant

data class HistoryUpdate(
    val chapterId: Long,
    val readAt: Instant,
    val sessionReadDuration: Long,
)
