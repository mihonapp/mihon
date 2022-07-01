package eu.kanade.tachiyomi.ui.manga.chapter.base

import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.model.Page

abstract class BaseChapterItem<T : BaseChapterHolder, H : AbstractHeaderItem<*>>(
    val chapter: Chapter,
    header: H? = null,
) : AbstractSectionableItem<T, H?>(header) {

    private var _status: Download.State = Download.State.NOT_DOWNLOADED

    var status: Download.State
        get() = download?.status ?: _status
        set(value) {
            _status = value
        }

    val progress: Int
        get() {
            val pages = download?.pages ?: return 0
            return pages.map(Page::progress).average().toInt()
        }

    @Transient
    var download: Download? = null

    val isDownloaded: Boolean
        get() = status == Download.State.DOWNLOADED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is BaseChapterItem<*, *>) {
            return chapter.id == other.chapter.id && chapter.read == other.chapter.read
        }
        return false
    }

    override fun hashCode(): Int {
        var result = chapter.id.hashCode()
        result = 31 * result + chapter.read.hashCode()
        return result
    }
}
