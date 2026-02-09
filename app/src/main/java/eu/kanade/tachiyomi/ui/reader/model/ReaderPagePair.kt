package eu.kanade.tachiyomi.ui.reader.model

/**
 * A pair of pages to be shown side-by-side.
 */
data class ReaderPagePair(
    val first: ReaderPage,
    val second: ReaderPage? = null
)
