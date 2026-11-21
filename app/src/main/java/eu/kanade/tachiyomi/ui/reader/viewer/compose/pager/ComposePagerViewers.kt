package eu.kanade.tachiyomi.ui.reader.viewer.compose.pager

import eu.kanade.tachiyomi.ui.reader.ReaderActivity

/**
 * Implementation of a left to right [ComposePagerViewer].
 */
class ComposeL2RPagerViewer(activity: ReaderActivity) : ComposePagerViewer(activity)

/**
 * Implementation of a right to left [ComposePagerViewer].
 */
class ComposeR2LPagerViewer(activity: ReaderActivity) : ComposePagerViewer(activity) {
    override val isReversed = true
}

/**
 * Implementation of a vertical (top to bottom) [ComposePagerViewer].
 */
class ComposeVerticalPagerViewer(activity: ReaderActivity) : ComposePagerViewer(activity, isHorizontal = false)
