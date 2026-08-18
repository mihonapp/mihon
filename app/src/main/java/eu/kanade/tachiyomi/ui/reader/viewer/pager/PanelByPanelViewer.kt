package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelDetector
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelDirection

/**
 * Implementation of a PagerViewer that navigates panel-by-panel within each page before
 * flipping to the next page, generalizing the dual-page-split pan mechanism in
 * [eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView] to N detected panel stops.
 */
class PanelByPanelViewer(activity: ReaderActivity) : PagerViewer(activity) {

    val panelDetector = PanelDetector(
        context = activity.applicationContext,
        panelCacheRepository = graph.panelCacheRepository,
    )

    // ReadingMode.PANEL_BY_PANEL has no direction of its own (unlike LEFT_TO_RIGHT/RIGHT_TO_LEFT),
    // so panel order is tracked by a dedicated preference instead.
    val panelDirection: PanelDirection
        get() = if (readerPreferences.panelByPanelRightToLeft.get()) PanelDirection.RTL else PanelDirection.LTR

    override fun createPager(): Pager = Pager(activity)

    override fun destroy() {
        super.destroy()
        panelDetector.close()
    }
}
