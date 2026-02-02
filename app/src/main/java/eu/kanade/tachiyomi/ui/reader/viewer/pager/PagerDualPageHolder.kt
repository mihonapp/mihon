package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import eu.kanade.tachiyomi.ui.reader.model.DualPage
import eu.kanade.tachiyomi.widget.ViewPagerAdapter

/**
 * A view that contains two [PagerPageHolder]s to be displayed side-by-side.
 */
@SuppressLint("ViewConstructor")
class PagerDualPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val dualPage: DualPage,
) : LinearLayout(readerThemedContext), ViewPagerAdapter.PositionableView {

    override val item: Any
        get() = dualPage

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        val isRtl = viewer is R2LPagerViewer
        layoutDirection = LAYOUT_DIRECTION_LTR

        val firstPageView = PagerPageHolder(readerThemedContext, viewer, dualPage.first)
        val secondPageView = dualPage.second?.let { PagerPageHolder(readerThemedContext, viewer, it) }

        if (secondPageView == null) {
            // Single page (e.g. Cover) - center it
            addView(View(readerThemedContext), LayoutParams(0, LayoutParams.MATCH_PARENT, 0.5f))
            addView(firstPageView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(View(readerThemedContext), LayoutParams(0, LayoutParams.MATCH_PARENT, 0.5f))
        } else {
            if (isRtl) {
                // In RTL, the first page of a spread (earlier) is on the right
                addView(secondPageView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                addView(firstPageView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            } else {
                // In LTR, the first page of a spread (earlier) is on the left
                addView(firstPageView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                addView(secondPageView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
        }
    }
}
