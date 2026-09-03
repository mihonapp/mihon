package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import ca.mpreg.webgpuviewer.ImageViewContinuous
import ca.mpreg.webgpuviewer.viewer.ImagePage
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuousState
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.max

class WebGpuViewerContinuous(activity: ReaderActivity) :
    WebGpuViewer(activity, isReversed = false, isVertical = true, pager = ImageViewContinuous(activity)) {

    override val isContinuous: Boolean = true

    // How many pages the viewport shows depends on the zoom, and a page on screen has to be
    // decoded rather than merely reserved - so the window follows what the last frame reached.
    override val preloadAhead get() = max(3, state.pagesBelow)
    override val preloadBehind get() = max(1, state.pagesAbove)

    // The state reaches MAX_VISIBLE_PAGES either side of the current page whatever the zoom - to
    // measure the document's end as well as to draw - and every page in that reach is created on
    // demand here. Sized under it, each frame evicts exactly what the next one asks for.
    override val cacheSize get() = 2 + 2 * ImageViewerContinuousState.MAX_VISIBLE_PAGES

    private val state get() = (pager as ImageViewContinuous).state

    init {
        // Scrolling clear of a transition page is the only point this mode can call the chapter
        // before it finished - reaching a page's top comes a screen too early. Reported on every
        // change, so scrolling back up over it and down again selects that last page again.
        state.onPageScrolledThrough = onScrolledThrough@{ imagePage ->
            val chapter = (imagePage as? TransitionPage)?.prevChapter ?: return@onScrolledThrough
            val lastPage = chapter.pages?.lastOrNull() ?: return@onScrolledThrough
            activity.onPageSelected(lastPage)
        }
    }

    private fun scrollByHalfPage(direction: Int) {
        state.animateScroll(direction * state.height / 2f)
    }

    override fun moveRight() = scrollByHalfPage(1)

    override fun moveLeft() = scrollByHalfPage(-1)

    override fun moveToPage(page: ReaderPage) {
        super.moveToPage(page)
        // Also for a jump to the page already showing, which turns nothing to slide in.
        state.resetScroll()
    }

    override fun animateTurn(direction: Int, fromSpread: ImagePage) {
        state.animateSlideIn(direction)
    }
}
