package eu.kanade.tachiyomi.ui.recent_updates

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import eu.kanade.tachiyomi.data.download.model.Download

class RecentChapter(mc: MangaChapter) : Chapter by mc.chapter {

    val manga = mc.manga

    private var _status: Int = 0

    var status: Int
        get() = download?.status ?: _status
        set(value) { _status = value }

    @Transient var download: Download? = null

    val isDownloaded: Boolean
        get() = status == Download.DOWNLOADED

}