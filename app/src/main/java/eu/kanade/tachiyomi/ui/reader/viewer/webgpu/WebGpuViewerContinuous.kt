package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import ca.mpreg.webgpuviewer.ImageViewContinuous
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.launch

class WebGpuViewerContinuous(activity: ReaderActivity) :
    WebGpuViewer(activity, isReversed = false, isVertical = true, pager = ImageViewContinuous(activity)) {

    override fun updateTransitionAnimation() {}

    private fun scrollByHalfPage(direction: Int) {
        val state = (pager as ImageViewContinuous).state
        val totalDistance = direction * state.height / 2f

        state.animationJob?.cancel()
        state.animationJob = state.scope?.launch {
            var lastValue = 0f
            animate(0f, totalDistance, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                state.scrollBy(value - lastValue)
                lastValue = value
                state.invalidate()
            }
        }
    }

    override fun moveRight() = scrollByHalfPage(1)

    override fun moveLeft() = scrollByHalfPage(-1)

    override fun moveToPage(page: ReaderPage) {
        super.moveToPage(page)
        (pager as ImageViewContinuous).state.scrollY = 0f
    }
}
