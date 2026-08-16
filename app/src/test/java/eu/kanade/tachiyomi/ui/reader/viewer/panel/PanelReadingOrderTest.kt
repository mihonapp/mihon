package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelReadingOrderTest {

    private val topLeft = PanelRect(0f, 0f, 0.5f, 0.5f)
    private val topRight = PanelRect(0.5f, 0f, 1f, 0.5f)
    private val bottomLeft = PanelRect(0f, 0.5f, 0.5f, 1f)
    private val bottomRight = PanelRect(0.5f, 0.5f, 1f, 1f)

    @Test
    fun `orders a 2x2 grid left-to-right per row for LTR`() {
        val shuffled = listOf(bottomRight, topLeft, bottomLeft, topRight)

        val ordered = PanelReadingOrder.sort(shuffled, PanelDirection.LTR)

        assertEquals(listOf(topLeft, topRight, bottomLeft, bottomRight), ordered)
    }

    @Test
    fun `orders a 2x2 grid right-to-left per row for RTL`() {
        val shuffled = listOf(bottomRight, topLeft, bottomLeft, topRight)

        val ordered = PanelReadingOrder.sort(shuffled, PanelDirection.RTL)

        assertEquals(listOf(topRight, topLeft, bottomRight, bottomLeft), ordered)
    }

    @Test
    fun `a full-width panel on top stays its own row before the split row below`() {
        val topBanner = PanelRect(0f, 0f, 1f, 0.4f)
        val shuffled = listOf(bottomRight, bottomLeft, topBanner)

        val ordered = PanelReadingOrder.sort(shuffled, PanelDirection.LTR)

        assertEquals(listOf(topBanner, bottomLeft, bottomRight), ordered)
    }

    @Test
    fun `empty input returns empty output`() {
        assertEquals(emptyList<PanelRect>(), PanelReadingOrder.sort(emptyList(), PanelDirection.LTR))
    }
}
