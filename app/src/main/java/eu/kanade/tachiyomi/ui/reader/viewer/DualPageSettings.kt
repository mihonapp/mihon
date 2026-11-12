package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * How a viewer shows a wide page.
 */
interface DualPageSettings {
    val dualPageSplit: Boolean
    val dualPageInvert: Boolean
    val dualPageRotateToFit: Boolean
    val dualPageRotateToFitInvert: Boolean
}
