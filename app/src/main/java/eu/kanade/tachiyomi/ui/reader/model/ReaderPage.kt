package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    /** Whether this page is shifted to sit alone, as if it were too wide to be doubled up. */
    var shiftedPage: Boolean = false,
    /** Whether this page can be doubled up, but can't because the next page is too wide. */
    var isolatedPage: Boolean = false,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null), ReaderItem {

    open lateinit var chapter: ReaderChapter

    /** Whether this page is too wide to be doubled up and must occupy a spread slot alone. */
    var fullPage: Boolean = false
        set(value) {
            field = value
            if (value) shiftedPage = false
        }
}
