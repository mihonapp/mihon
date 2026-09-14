package eu.kanade.tachiyomi.ui.reader.cast

import android.view.Choreographer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import ca.mpreg.webgpuviewer.ImageViewContinuous
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuousState
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewerAdapter
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonAdapter
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Connects the reader's active [Viewer] to the [CastController].
 *
 * Reports what the viewers don't already tell [ReaderActivity]: the scroll offset inside the page
 * at the top of continuous viewers and the chapter transitions of paged ones (pages themselves
 * arrive through [ReaderActivity.onPageSelected]). Also executes the navigation commands coming
 * from cast targets, the remote sheet and the auto-scroller.
 */
class CastViewerBridge(
    private val activity: ReaderActivity,
    private val controller: CastController,
) {

    private var viewer: Viewer? = null
    private var hook: Hook? = null

    private var lastScrollPage: ReaderPage? = null
    private var lastScrollFraction = -1f

    private val screenStep: Int
        get() = activity.resources.displayMetrics.heightPixels * 3 / 4

    /** True for viewers that scroll freely (webtoon / continuous vertical) rather than by page. */
    val isContinuous: Boolean
        get() = when (val viewer = viewer) {
            is WebtoonViewer -> true
            is WebGpuViewer -> viewer.isContinuous
            else -> false
        }

    fun attach(viewer: Viewer) {
        detach()
        this.viewer = viewer
        hook = when (viewer) {
            is WebtoonViewer -> WebtoonHook(viewer)
            is PagerViewer -> PagerHook(viewer)
            is WebGpuViewer -> WebGpuHook(viewer)
            else -> null
        }?.also { it.install() }
    }

    fun detach() {
        hook?.uninstall()
        hook = null
        viewer = null
        lastScrollPage = null
        lastScrollFraction = -1f
    }

    /** Moves forward in reading order: a page for paged viewers, most of a screen for continuous ones. */
    fun next() {
        when (val viewer = viewer) {
            is WebtoonViewer -> viewer.recycler.smoothScrollBy(0, screenStep)
            is PagerViewer -> viewer.moveToNext()
            // WebGpuViewer's moveToNext/moveToPrevious are spatial (right/left), not reading order.
            is WebGpuViewer -> if (viewer.isReversed) viewer.moveToPrevious() else viewer.moveToNext()
        }
    }

    fun previous() {
        when (val viewer = viewer) {
            is WebtoonViewer -> viewer.recycler.smoothScrollBy(0, -screenStep)
            is PagerViewer -> viewer.moveToPrevious()
            is WebGpuViewer -> if (viewer.isReversed) viewer.moveToNext() else viewer.moveToPrevious()
        }
    }

    /**
     * Scrolls continuous viewers by [deltaPx] device pixels (positive = down); paged viewers turn a
     * page by sign. Returns false when the viewer sits at its edge and could not move.
     */
    fun scrollBy(deltaPx: Float): Boolean {
        if (deltaPx == 0f) return true
        return when (val viewer = viewer) {
            is WebtoonViewer -> (hook as? WebtoonHook)?.scrollBy(deltaPx.roundToInt()) ?: false
            is WebGpuViewer if viewer.isContinuous -> {
                val state = viewer.continuousState ?: return false
                val scale = state.scale.takeIf { it > 0f } ?: 1f
                val before = state.documentY
                state.scrollBy(deltaPx / scale)
                state.invalidate()
                state.documentY != before
            }
            null -> false
            else -> {
                val canMove = deltaPx < 0f || canScrollForward()
                if (deltaPx > 0f) next() else previous()
                canMove
            }
        }
    }

    /** Whether there is content after the current position in reading order. */
    fun canScrollForward(): Boolean {
        return when (val viewer = viewer) {
            is WebtoonViewer -> viewer.recycler.canScrollVertically(1)
            is PagerViewer -> {
                val pager = viewer.pager
                val count = pager.adapter?.count ?: 0
                if (viewer is R2LPagerViewer) pager.currentItem > 0 else pager.currentItem < count - 1
            }
            is WebGpuViewer -> !viewer.isAtEnd()
            else -> false
        }
    }

    private fun reportScroll(page: ReaderPage, fraction: Float) {
        if (page === lastScrollPage && abs(fraction - lastScrollFraction) <= SCROLL_EPSILON) return
        lastScrollPage = page
        lastScrollFraction = fraction
        controller.onReaderScroll(page, fraction)
    }

    private fun reportTransition(from: ReaderChapter, to: ReaderChapter?, forward: Boolean) {
        // Scrolling back onto the same page position must be reported again after a transition.
        lastScrollPage = null
        controller.onReaderTransition(from, to, forward)
    }

    private fun WebGpuViewer.isAtEnd(): Boolean {
        val page = currentPage as? WebGpuViewer.ViewerTransitionPage ?: return false
        return page.nextChapter == null
    }

    private val WebGpuViewer.continuousState: ImageViewerContinuousState?
        get() = (pager as? ImageViewContinuous)?.state

    private interface Hook {
        fun install()
        fun uninstall()
    }

    private inner class WebtoonHook(viewer: WebtoonViewer) : Hook {

        private val recycler = viewer.recycler
        private var attached = false

        /** Pixels the recycler actually scrolled during the last [scrollBy]. */
        private var consumedDy = 0

        private val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                consumedDy += dy
                report()
            }
        }

        private val initialReport = Runnable { if (attached) report() }

        override fun install() {
            attached = true
            recycler.addOnScrollListener(scrollListener)
            recycler.post(initialReport)
        }

        override fun uninstall() {
            attached = false
            recycler.removeCallbacks(initialReport)
            recycler.removeOnScrollListener(scrollListener)
        }

        fun scrollBy(dy: Int): Boolean {
            if (dy == 0) return true
            consumedDy = 0
            recycler.scrollBy(0, dy)
            return consumedDy != 0
        }

        private fun report() {
            val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
            val adapter = recycler.adapter as? WebtoonAdapter ?: return
            val position = layoutManager.findFirstVisibleItemPosition()
            if (position == RecyclerView.NO_POSITION) return
            when (val item = adapter.items.getOrNull(position)) {
                is InsertPage -> Unit
                is ReaderPage -> reportScroll(item, fractionScrolledPast(layoutManager, position))
                is ChapterTransition -> reportTransition(item.from, item.to, forward = item is ChapterTransition.Next)
            }
        }

        /** How much (0..1) of the item at [position] has scrolled past the top of the viewport. */
        private fun fractionScrolledPast(layoutManager: LinearLayoutManager, position: Int): Float {
            val child = layoutManager.findViewByPosition(position) ?: return 0f
            val top = layoutManager.getDecoratedTop(child) - recycler.paddingTop
            val height = layoutManager.getDecoratedMeasuredHeight(child)
            if (height <= 0) return 0f
            return (-top.toFloat() / height).coerceIn(0f, 1f)
        }
    }

    private inner class PagerHook(private val viewer: PagerViewer) : Hook {

        private val pageListener = object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                val adapter = viewer.pager.adapter as? PagerViewerAdapter ?: return
                val item = adapter.items.getOrNull(position) as? ChapterTransition ?: return
                reportTransition(item.from, item.to, forward = item is ChapterTransition.Next)
            }
        }

        override fun install() = viewer.pager.addOnPageChangeListener(pageListener)

        override fun uninstall() = viewer.pager.removeOnPageChangeListener(pageListener)
    }

    /**
     * The WebGPU viewers expose no scroll or page listener, so the position is polled once per
     * frame while attached (a few volatile reads) and only reported when it changed.
     */
    private inner class WebGpuHook(private val viewer: WebGpuViewer) : Hook, Choreographer.FrameCallback {

        private val choreographer = Choreographer.getInstance()
        private var attached = false
        private var lastTransition: WebGpuViewer.ViewerTransitionPage? = null
        private var lastReaderPage: ReaderPage? = null

        override fun install() {
            attached = true
            choreographer.postFrameCallback(this)
        }

        override fun uninstall() {
            attached = false
            choreographer.removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!attached) return
            poll()
            choreographer.postFrameCallback(this)
        }

        private fun poll() {
            when (val current = viewer.currentPage) {
                is WebGpuViewer.ViewerReaderPage -> {
                    lastTransition = null
                    lastReaderPage = current.page
                    if (viewer.isContinuous) pollContinuous(current)
                }
                is WebGpuViewer.ViewerTransitionPage -> {
                    if (current === lastTransition) return
                    lastTransition = current
                    reportTransitionPage(current)
                }
                else -> Unit
            }
        }

        private fun pollContinuous(current: WebGpuViewer.ViewerReaderPage) {
            val state = viewer.continuousState ?: return
            val scrollY = state.scrollY
            // A page crossing between the two reads would pair the offset with the wrong page.
            if (viewer.currentPage !== current) return
            val slotHeight = state.getPageSlotHeight(current.imagePage)
            val fraction = if (slotHeight > 0f) (scrollY / slotHeight).coerceIn(0f, 1f) else 0f
            reportScroll(current.page, fraction)
        }

        private fun reportTransitionPage(transition: WebGpuViewer.ViewerTransitionPage) {
            val prev = transition.prevChapter
            val next = transition.nextChapter
            when {
                prev != null && next != null -> {
                    // Arriving from the later chapter means the user is going back.
                    if (lastReaderPage?.chapter == next) {
                        reportTransition(next, prev, forward = false)
                    } else {
                        reportTransition(prev, next, forward = true)
                    }
                }
                prev != null -> reportTransition(prev, null, forward = true)
                next != null -> reportTransition(next, null, forward = false)
            }
        }
    }

    companion object {
        /** Minimum change of the scroll fraction worth pushing to the cast targets. */
        private const val SCROLL_EPSILON = 0.002f
    }
}
