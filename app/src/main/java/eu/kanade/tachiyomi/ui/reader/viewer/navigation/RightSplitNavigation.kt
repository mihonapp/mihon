package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import android.graphics.RectF
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * Split right navigation:
 * Right 1/3rd of the screen is split horizontally:
 * - Top half: Previous
 * - Bottom half: Next
 */
class RightSplitNavigation : ViewerNavigation() {

    override var regionList: List<Region> = listOf(
        Region(
            rectF = RectF(regionSize2, 0f, 1f, 0.5f),
            type = NavigationRegion.PREV,
        ),
        Region(
            rectF = RectF(regionSize2, 0.5f, 1f, 1f),
            type = NavigationRegion.NEXT,
        ),
    )
}
