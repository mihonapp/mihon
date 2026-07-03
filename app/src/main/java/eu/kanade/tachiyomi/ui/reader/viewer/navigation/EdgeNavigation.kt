package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import android.graphics.RectF
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion
 * +---+---+---+
 * | N | N | N |   P: Previous
 * +---+---+---+
 * | N | M | N |   M: Menu
 * +---+---+---+
 * | N | P | N |   N: Next
 * +---+---+---+
*/
class EdgeNavigation : ViewerNavigation() {

    override var regionList: List<Region> = listOf(
        Region(
            rectF = RectF(0f, 0f, regionSize1, 1f),
            type = NavigationRegion.NEXT,
        ),
        Region(
            rectF = RectF(regionSize1, regionSize2, regionSize2, 1f),
            type = NavigationRegion.PREV,
        ),
        Region(
            rectF = RectF(regionSize2, 0f, 1f, 1f),
            type = NavigationRegion.NEXT,
        ),
    )
}
