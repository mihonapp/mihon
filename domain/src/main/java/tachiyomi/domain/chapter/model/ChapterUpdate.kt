package tachiyomi.domain.chapter.model

import mihon.domain.common.PartialUpdate
import kotlin.time.Instant

class ChapterUpdate(val id: Long, block: ChapterUpdate.() -> Unit) : PartialUpdate() {
    var read: Boolean? by field(null)
    var bookmark: Boolean? by field(null)
    var lastPageRead: Int? by field(null)
    var dateFetch: Instant? by field(null)

    init {
        block()
    }
}
