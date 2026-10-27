package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

/**
 * Settings every paged viewer uses.
 */
data class PagedSettings(
    val page: Page,
    val navigateToPan: Boolean,
    val navigation: NavigationSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val imageScaleType: ReaderPreferences.ScaleType,
        val zoomStart: ReaderPreferences.ZoomStartType,
        val cropBorders: Boolean,
        val landscapeZoom: Boolean,
    )
}
