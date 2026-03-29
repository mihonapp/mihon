package tachiyomi.domain.history.model

import java.util.Date

data class HistoryUpdate(
    val chapterId: Long,
    val readAt: Date,
    val sessionReadDuration: Long,
)

fun History.toHistoryUpdate(): HistoryUpdate {
    return HistoryUpdate(
        chapterId,
        readAt ?: Date(0),
        readDuration,
    )
}
