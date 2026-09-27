package tachiyomi.domain.chapter.model

import mihon.domain.common.PartialUpdate

class ChapterUpdate(val id: Long, block: ChapterUpdate.() -> Unit) : PartialUpdate() {
    var read: Boolean? by field(null)
    var bookmark: Boolean? by field(null)
    var lastPageRead: Long? by field(null)
    var dateFetch: Long? by field(null)

    init {
        block()
    }
}
