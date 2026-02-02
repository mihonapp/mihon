package eu.kanade.tachiyomi.ui.reader.model

/**
 * A model that wraps two [ReaderPage] objects to be displayed together.
 */
data class DualPage(
    val first: ReaderPage,
    val second: ReaderPage? = null,
)
