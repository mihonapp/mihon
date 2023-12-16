package tachiyomi.domain.history.model

import java.util.Date

data class History(
    val id: Long,
    val chapterId: Long,
    val readAt: Date?,
    val readDuration: Long,
) {
    companion object {
        fun create() = History(
            id = -1L,
            chapterId = -1L,
            readAt = null,
            readDuration = -1L,
        )
    }
}
