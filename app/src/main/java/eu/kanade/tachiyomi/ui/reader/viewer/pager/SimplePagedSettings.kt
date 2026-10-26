package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Settings only the pager viewers use, all of them part of what the pages are made with.
 */
data class SimplePagedSettings(
    val dualPageSplit: Boolean,
    val dualPageInvert: Boolean,
    val dualPageRotateToFit: Boolean,
    val dualPageRotateToFitInvert: Boolean,
)
