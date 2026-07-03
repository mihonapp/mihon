package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import android.graphics.RectF
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion
 * +---+---+---+
 * | P | P | P |   P: Previous
 * +---+---+---+
 * | P | M | N |   M: Menu
 * +---+---+---+
 * | N | N | N |   N: Next
 * +---+---+---+
 */
open class LNavigation : ViewerNavigation() {

    override var regionList: List<Region> = listOf(
        Region(
            rectF = RectF(0f, regionSize1, regionSize1, regionSize2),
            type = NavigationRegion.PREV,
        ),
        Region(
            rectF = RectF(0f, 0f, 1f, regionSize1),
            type = NavigationRegion.PREV,
        ),
        Region(
            rectF = RectF(regionSize2, regionSize1, 1f, regionSize2),
            type = NavigationRegion.NEXT,
        ),
        Region(
            rectF = RectF(0f, regionSize2, 1f, 1f),
            type = NavigationRegion.NEXT,
        ),
    )
}
