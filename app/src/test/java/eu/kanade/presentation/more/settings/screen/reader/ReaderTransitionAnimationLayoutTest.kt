package eu.kanade.presentation.more.settings.screen.reader

import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Guards the curve-editor's edge clearances so a draggable element (grid handle or slider thumb) can't
 * regress into the system back-gesture zone at the screen edge. See the constants in
 * [ReaderTransitionAnimationScreen] for the reasoning behind each inset.
 */
@Execution(ExecutionMode.CONCURRENT)
class ReaderTransitionAnimationLayoutTest {

    @Test
    fun `grid keeps a handle clear of the back-gesture edge`() {
        // A handle is effectively a point, so its clearance is section padding + the grid's own inset.
        val gridClearance = ReaderTransitionAnimationScreen.CONTENT_HORIZONTAL_PADDING +
            ReaderTransitionAnimationScreen.EDGE_GESTURE_INSET
        gridClearance shouldBeGreaterThanOrEqualTo ReaderTransitionAnimationScreen.MIN_EDGE_CLEARANCE
    }

    @Test
    fun `slider keeps the whole thumb clear of the back-gesture edge`() {
        // The thumb reaches ~its radius past the track end toward the edge, so subtract that from the
        // section padding + slider inset before comparing to the required clearance.
        val thumbEdgeClearance = ReaderTransitionAnimationScreen.CONTENT_HORIZONTAL_PADDING +
            ReaderTransitionAnimationScreen.SLIDER_EDGE_INSET -
            ReaderTransitionAnimationScreen.SLIDER_THUMB_RADIUS
        thumbEdgeClearance shouldBeGreaterThanOrEqualTo ReaderTransitionAnimationScreen.MIN_EDGE_CLEARANCE
    }

    @Test
    fun `sliders sit further from the edge than the grid to account for thumb width`() {
        ReaderTransitionAnimationScreen.SLIDER_EDGE_INSET shouldBeGreaterThanOrEqualTo
            ReaderTransitionAnimationScreen.EDGE_GESTURE_INSET
    }
}
