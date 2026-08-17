package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelDetector
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelDirection

/**
 * Implementation of a PagerViewer that navigates panel-by-panel within each page before
 * flipping to the next page, generalizing the dual-page-split pan mechanism in
 * [eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView] to N detected panel stops.
 */
class PanelByPanelViewer(activity: ReaderActivity) : PagerViewer(activity) {

    val panelDetector = PanelDetector(panelCacheRepository = graph.panelCacheRepository)

    val panelDirection: PanelDirection
        get() = if (
            ReadingMode.fromPreference(readerPreferences.defaultReadingMode.get()) == ReadingMode.RIGHT_TO_LEFT
        ) {
            PanelDirection.RTL
        } else {
            PanelDirection.LTR
        }

    override fun createPager(): Pager = Pager(activity)
}
