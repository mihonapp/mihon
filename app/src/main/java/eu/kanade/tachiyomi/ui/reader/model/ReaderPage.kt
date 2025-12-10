package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    text: String? = null,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null, text) {

    open lateinit var chapter: ReaderChapter
}
