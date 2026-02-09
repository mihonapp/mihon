package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderItemPair
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer

/**
 * A holder that contains two items (Pages or Transitions) side-by-side.
 */
@SuppressLint("ViewConstructor")
class PagerPagePairHolder(
    private val readerThemedContext: Context,
    val viewer: PagerViewer,
    val pair: ReaderItemPair,
) : SideBySideLayout(readerThemedContext), ViewPagerAdapter.PositionableView {

    override val item: Any
        get() = pair

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val isRtl = viewer is R2LPagerViewer
        val gapWidth = viewer.activity.getHingeGap()
        val centerSingle = viewer.activity.readerPreferences.centerSinglePage().get()

        // Helper for flexible width (0 width -> handled by SideBySideLayout)
        fun flexParams() = ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)

        if (pair.second == null && gapWidth == 0 && centerSingle) {
            centerSingleChild = true
            addView(createItemView(pair.first), flexParams())
        } else if (isRtl) {
            // R2L: [Second/Dummy] [Gap] [First]
            if (pair.second != null) {
                addView(createItemView(pair.second), flexParams())
            } else {
                addView(View(context).apply { minimumWidth = 1 }, flexParams())
            }

            if (gapWidth > 0) {
                addView(View(context).apply {
                    layoutParams = ViewGroup.LayoutParams(gapWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                })
            }

            addView(createItemView(pair.first), flexParams())
        } else {
            // L2R: [First] [Gap] [Second/Dummy]
            addView(createItemView(pair.first), flexParams())

            if (gapWidth > 0) {
                addView(View(context).apply {
                    layoutParams = ViewGroup.LayoutParams(gapWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                })
            }

            if (pair.second != null) {
                addView(createItemView(pair.second), flexParams())
            } else {
                addView(View(context).apply { minimumWidth = 1 }, flexParams())
            }
        }
    }

    private fun createItemView(item: Any): View {
        return when (item) {
            is ReaderPage -> PagerPageHolder(readerThemedContext, viewer, item)
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }
}