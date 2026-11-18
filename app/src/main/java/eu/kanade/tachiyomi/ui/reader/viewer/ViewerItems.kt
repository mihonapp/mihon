package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters

/**
 * The items of a viewer for [chapters], top to bottom: the previous chapter's pages, its transition, [currentPages],
 * then the next transition and chapter's pages, so moving between chapters is seamless. A transition is left out
 * when its chapter is loaded and adjacent, unless [forceTransition].
 */
fun viewerItems(
    chapters: ViewerChapters,
    forceTransition: Boolean,
    currentPages: List<ReaderPage>? = chapters.currChapter.pages,
): MutableList<Any> {
    val items = mutableListOf<Any>()

    // Forces chapter transition if there is missing chapters
    val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
    val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

    // Add previous chapter pages and transition
    chapters.prevChapter?.pages?.let(items::addAll)

    // Skip transition page if the chapter is loaded & current page is not a transition page
    if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
        items.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
    }

    // Add current chapter.
    currentPages?.let(items::addAll)

    // Add next chapter transition and pages.
    if (nextHasMissingChapters || forceTransition || chapters.nextChapter?.state !is ReaderChapter.State.Loaded) {
        items.add(ChapterTransition.Next(chapters.currChapter, chapters.nextChapter))
    }

    chapters.nextChapter?.pages?.let(items::addAll)

    return items
}
