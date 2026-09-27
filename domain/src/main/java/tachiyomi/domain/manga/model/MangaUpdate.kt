package tachiyomi.domain.manga.model

import mihon.domain.common.PartialUpdate

class MangaUpdate(val id: Long, block: MangaUpdate.() -> Unit) : PartialUpdate() {
    var favorite: Boolean? by field(null)
    var lastUpdate: Long? by field(null)
    var nextUpdate: Long? by field(null)
    var fetchInterval: Int? by field(null)
    var dateAdded: Long? by field(null)
    var viewerFlags: Long? by field(null)
    var chapterFlags: Long? by field(null)
    var coverLastModified: Long? by field(null)
    var notes: String? by field(null)

    init {
        block()
    }
}
