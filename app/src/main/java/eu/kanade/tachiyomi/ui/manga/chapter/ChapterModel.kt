package eu.kanade.tachiyomi.ui.manga.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.model.Download

class ChapterModel(c: Chapter) : Chapter by c {

    private var _status: Int = 0

    var status: Int
        get() = download?.status ?: _status
        set(value) { _status = value }

    @Transient var download: Download? = null

    val isDownloaded: Boolean
        get() = status == Download.DOWNLOADED

}