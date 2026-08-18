package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelOrderingTest {

    private val topLeft = PanelRect(0.0f, 0.0f, 0.45f, 0.4f)
    private val topRight = PanelRect(0.55f, 0.0f, 1.0f, 0.4f)
    private val bottomLeft = PanelRect(0.0f, 0.5f, 0.45f, 1.0f)
    private val bottomRight = PanelRect(0.55f, 0.5f, 1.0f, 1.0f)

    @Test
    fun ordersRowsTopDownThenLeftToRight() {
        val shuffled = listOf(bottomRight, topRight, bottomLeft, topLeft)
        val ordered = PanelOrdering.order(shuffled)
        assertEquals(listOf(topLeft, topRight, bottomLeft, bottomRight), ordered)
    }

    @Test
    fun rightToLeftReversesWithinRowsOnly() {
        val shuffled = listOf(bottomRight, topRight, bottomLeft, topLeft)
        val ordered = PanelOrdering.order(shuffled, rightToLeft = true)
        assertEquals(listOf(topRight, topLeft, bottomRight, bottomLeft), ordered)
    }

    @Test
    fun slightlyMisalignedPanelsStillShareARow() {
        // Vertical ranges overlap well over half of the shorter panel's height.
        val left = PanelRect(0.0f, 0.10f, 0.45f, 0.45f)
        val right = PanelRect(0.55f, 0.05f, 1.0f, 0.40f)
        val ordered = PanelOrdering.order(listOf(right, left))
        assertEquals(listOf(left, right), ordered)
    }

    @Test
    fun barelyOverlappingPanelsFormSeparateRows() {
        // Overlap is far below half of the shorter panel's height → two rows, top first.
        val upper = PanelRect(0.5f, 0.0f, 1.0f, 0.32f)
        val lower = PanelRect(0.0f, 0.3f, 0.45f, 0.7f)
        val ordered = PanelOrdering.order(listOf(lower, upper))
        assertEquals(listOf(upper, lower), ordered)
    }

    @Test
    fun staggeredBottomRowUnderAnOverlappingPanelReadsLeftToRight() {
        // A huge panel overlaps everything (no clean cut), with a bottom row whose two panels are
        // vertically staggered. The bottom row must read left→right, not "higher-first".
        val huge = PanelRect(0.0f, 0.01f, 1.0f, 0.84f)
        val bottomRightHigher = PanelRect(0.48f, 0.77f, 0.89f, 0.98f) // starts higher
        val bottomLeftLower = PanelRect(0.0f, 0.84f, 0.48f, 1.0f) // starts lower, but is on the left
        val ordered = PanelOrdering.order(listOf(bottomRightHigher, huge, bottomLeftLower))
        assertEquals(listOf(huge, bottomLeftLower, bottomRightHigher), ordered)
    }

    @Test
    fun emptyAndSingleListsPassThrough() {
        assertEquals(emptyList<PanelRect>(), PanelOrdering.order(emptyList()))
        assertEquals(listOf(topLeft), PanelOrdering.order(listOf(topLeft)))
    }
}
