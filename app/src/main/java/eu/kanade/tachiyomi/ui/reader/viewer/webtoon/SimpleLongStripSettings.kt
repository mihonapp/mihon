package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.DualPageSettings
import eu.kanade.tachiyomi.ui.reader.viewer.NavigationSettings

/**
 * Settings only the webtoon viewers use.
 */
data class SimpleLongStripSettings(
    val page: Page,
    val doubleTapZoom: Boolean,
    val navigation: NavigationSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val sidePadding: Int,
        override val dualPageSplit: Boolean,
        override val dualPageInvert: Boolean,
        override val dualPageRotateToFit: Boolean,
        override val dualPageRotateToFitInvert: Boolean,
    ) : DualPageSettings
}
