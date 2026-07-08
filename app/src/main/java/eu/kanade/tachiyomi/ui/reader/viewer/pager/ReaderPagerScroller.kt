package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.animation.Interpolator
import android.widget.Scroller

/**
 * [Scroller] used to customize the page-transition feel of the paged viewer. It applies a custom
 * [interpolator] (the chosen Bézier curve) and forces a fixed [durationMs] for every settle, instead
 * of the distance-based duration the ViewPager would compute. A new instance is created whenever the
 * curve changes because a [Scroller]'s interpolator can only be set at construction time.
 */
class ReaderPagerScroller(
    context: Context,
    interpolator: Interpolator,
    private val durationMs: Int,
) : Scroller(context, interpolator) {

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        super.startScroll(startX, startY, dx, dy, durationMs)
    }
}
