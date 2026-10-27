package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import androidx.compose.ui.geometry.Rect
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TappingInvertMode
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
open class LNavigation(invertMode: TappingInvertMode, isReversed: Boolean) : ViewerNavigation(invertMode, isReversed) {

    override val regionList: List<Region> = listOf(
        Region(
            rect = Rect(0f, 0.33f, 0.33f, 0.66f),
            type = NavigationRegion.PREV,
        ),
        Region(
            rect = Rect(0f, 0f, 1f, 0.33f),
            type = NavigationRegion.PREV,
        ),
        Region(
            rect = Rect(0.66f, 0.33f, 1f, 0.66f),
            type = NavigationRegion.NEXT,
        ),
        Region(
            rect = Rect(0f, 0.66f, 1f, 1f),
            type = NavigationRegion.NEXT,
        ),
    )
}
