package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelTest {

    @Test
    fun `flattenToStops uses subStops when present, otherwise the panel bounds`() {
        val simple = Panel(bounds = PanelRect(0f, 0f, 0.5f, 1f))
        val wide = Panel(
            bounds = PanelRect(0.5f, 0f, 1f, 1f),
            subStops = listOf(
                PanelRect(0.5f, 0f, 0.7f, 1f),
                PanelRect(0.5f, 0f, 1f, 1f),
            ),
        )

        val stops = listOf(simple, wide).flattenToStops()

        assertEquals(
            listOf(
                PanelRect(0f, 0f, 0.5f, 1f),
                PanelRect(0.5f, 0f, 0.7f, 1f),
                PanelRect(0.5f, 0f, 1f, 1f),
            ),
            stops,
        )
    }

    @Test
    fun `PanelRect width and height are computed from bounds`() {
        val rect = PanelRect(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.6f)

        assertEquals(0.6f, rect.width, 0.0001f)
        assertEquals(0.5f, rect.height, 0.0001f)
    }
}
