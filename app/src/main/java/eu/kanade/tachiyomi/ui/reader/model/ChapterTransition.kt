package eu.kanade.tachiyomi.ui.reader.model

sealed class ChapterTransition {

    abstract val from: ReaderChapter
    abstract val to: ReaderChapter?

    class Prev(
            override val from: ReaderChapter, override val to: ReaderChapter?
    ) : ChapterTransition()
    class Next(
            override val from: ReaderChapter, override val to: ReaderChapter?
    ) : ChapterTransition()

    override fun toString(): String {
        return "${javaClass.simpleName}(from=${from.chapter.url}, to=${to?.chapter?.url})"
    }

}
