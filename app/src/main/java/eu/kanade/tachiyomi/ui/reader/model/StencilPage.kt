package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.util.system.ImageUtil

class StencilPage(
    parent: ReaderPage,
    val splitData: ImageUtil.SplitData,
) : ReaderPage(parent.index, parent.url, parent.imageUrl) {

    override var chapter: ReaderChapter = parent.chapter

    init {
        stream = parent.stream
    }
}
