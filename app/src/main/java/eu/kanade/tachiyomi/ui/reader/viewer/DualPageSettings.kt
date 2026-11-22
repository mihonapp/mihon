package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * How a viewer shows pages that are split or rotated.
 */
interface DualPageSettings {
    val dualPageSplit: Boolean
    val splitAllPages: Boolean
    val dualPageInvert: Boolean
    val dualPageRotateToFit: Boolean
    val dualPageRotateToFitInvert: Boolean
}
