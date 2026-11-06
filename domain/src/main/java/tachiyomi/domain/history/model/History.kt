package tachiyomi.domain.history.model

import kotlin.time.Instant

data class History(
    val id: Long,
    val chapterId: Long,
    val readAt: Instant?,
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
