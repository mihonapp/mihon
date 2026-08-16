package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometricPanelSubStopGeneratorTest {

    @Test
    fun `a narrow panel gets no sub-stops`() = runTest {
        val panel = PanelRect(0f, 0f, 0.4f, 0.5f) // aspect ratio 0.8

        val stops = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.LTR) { null }

        assertTrue(stops.isEmpty())
    }

    @Test
    fun `a wide panel gets ordered sub-stops ending with the full panel, LTR`() = runTest {
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f) // aspect ratio 6.0, wide spread

        val stops = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.LTR) { null }

        assertEquals(4, stops.size)
        assertEquals(panel, stops.last())
        for (i in 0 until stops.size - 2) {
            assertTrue(stops[i].left < stops[i + 1].left, "stops should move left-to-right")
        }
    }

    @Test
    fun `a wide panel orders sub-stops right-to-left before the full panel, RTL`() = runTest {
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f)

        val stops = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.RTL) { null }

        assertEquals(4, stops.size)
        assertEquals(panel, stops.last())
        for (i in 0 until stops.size - 2) {
            assertTrue(stops[i].left > stops[i + 1].left, "stops should move right-to-left")
        }
    }
}
