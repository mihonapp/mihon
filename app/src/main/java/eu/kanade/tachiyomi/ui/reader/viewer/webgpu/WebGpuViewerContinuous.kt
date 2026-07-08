package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import ca.mpreg.webgpuviewer.ImageViewContinuous
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

class WebGpuViewerContinuous(activity: ReaderActivity) :
    WebGpuViewer(activity, isReversed = false, isVertical = true, pager = ImageViewContinuous(activity)) {

    override fun updateTransitionAnimation() {}

    private fun scrollByHalfPage(direction: Int) {
        val state = (pager as ImageViewContinuous).state
        val totalDistance = direction * state.height / 2f
        state.animateScroll(totalDistance)
    }

    override fun moveRight() = scrollByHalfPage(1)

    override fun moveLeft() = scrollByHalfPage(-1)

    override fun moveToPage(page: ReaderPage) {
        super.moveToPage(page)
        (pager as ImageViewContinuous).state.scrollY = 0f
    }
}
