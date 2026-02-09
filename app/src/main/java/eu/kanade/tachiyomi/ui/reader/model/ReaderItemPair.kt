package eu.kanade.tachiyomi.ui.reader.model

/**
 * A pair of items (Pages or Transitions) to be shown side-by-side.
 */
data class ReaderItemPair(
    val first: Any,
    val second: Any? = null
)
