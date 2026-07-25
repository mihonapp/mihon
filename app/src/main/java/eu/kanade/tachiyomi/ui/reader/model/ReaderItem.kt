package eu.kanade.tachiyomi.ui.reader.model

/**
 * Marker for the two kinds of item a pager viewer can hold: a [ReaderPage] or a
 * [ChapterTransition]. Lets the pager adapter type its item/pair lists without resorting to [Any].
 */
sealed interface ReaderItem
