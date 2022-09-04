package eu.kanade.tachiyomi.ui.reader.model

import java.io.InputStream

class StencilPage(
    parent: ReaderPage,
    stencilStream: () -> InputStream,
) : ReaderPage(parent.index, parent.url, parent.imageUrl) {

    override var chapter: ReaderChapter = parent.chapter

    init {
        status = READY
        stream = stencilStream
    }
}
