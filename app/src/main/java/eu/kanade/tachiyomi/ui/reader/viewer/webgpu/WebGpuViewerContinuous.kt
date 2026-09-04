package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import ca.mpreg.webgpuviewer.ImageViewContinuous
import ca.mpreg.webgpuviewer.viewer.ImagePage
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

class WebGpuViewerContinuous(activity: ReaderActivity) :
    WebGpuViewer(activity, isReversed = false, isVertical = true, pager = ImageViewContinuous(activity)) {

    override val isContinuous: Boolean = true

    override val preloadAhead = 3
    override val preloadBehind = 1

    private val state get() = (pager as ImageViewContinuous).state

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
