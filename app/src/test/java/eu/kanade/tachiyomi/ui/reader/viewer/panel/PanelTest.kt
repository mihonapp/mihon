package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelTest {

    @Test
    fun `flattenToStops uses subStops when present, otherwise the panel bounds, then reveals the full page`() {
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
                PanelRect.FULL_PAGE,
            ),
            stops,
        )
    }

    @Test
    fun `flattenToStops does not duplicate the full-page reveal when the only panel already is the full page`() {
        // The detector's fallback case (nothing confidently detected, decode failure, timeout)
        // is exactly one Panel(PanelRect.FULL_PAGE) with no substops. Appending a second,
        // identical full-page stop would make the reader tap once for a no-op.
        val fallback = Panel(bounds = PanelRect.FULL_PAGE)

        val stops = listOf(fallback).flattenToStops()

        assertEquals(listOf(PanelRect.FULL_PAGE), stops)
    }

    @Test
    fun `flattenToStops adds a full-page intro before the first panel when enabled`() {
        val panels = listOf(Panel(bounds = PanelRect(0f, 0f, 0.5f, 1f)))

        val stops = panels.flattenToStops(showIntro = true, showOutro = false)

        assertEquals(listOf(PanelRect.FULL_PAGE, PanelRect(0f, 0f, 0.5f, 1f)), stops)
    }

    @Test
    fun `flattenToStops brackets both ends when intro and outro are both enabled`() {
        val panels = listOf(Panel(bounds = PanelRect(0f, 0f, 0.5f, 1f)))

        val stops = panels.flattenToStops(showIntro = true, showOutro = true)

        assertEquals(
            listOf(PanelRect.FULL_PAGE, PanelRect(0f, 0f, 0.5f, 1f), PanelRect.FULL_PAGE),
            stops,
        )
    }

    @Test
    fun `flattenToStops has no full-page reveal at all when both are disabled`() {
        val panels = listOf(Panel(bounds = PanelRect(0f, 0f, 0.5f, 1f)))

        val stops = panels.flattenToStops(showIntro = false, showOutro = false)

        assertEquals(listOf(PanelRect(0f, 0f, 0.5f, 1f)), stops)
    }

    @Test
    fun `flattenToStops with intro still collapses to a single stop for the no-panels fallback`() {
        val fallback = Panel(bounds = PanelRect.FULL_PAGE)

        val stops = listOf(fallback).flattenToStops(showIntro = true, showOutro = true)

        assertEquals(listOf(PanelRect.FULL_PAGE), stops)
    }

    @Test
    fun `PanelRect width and height are computed from bounds`() {
        val rect = PanelRect(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.6f)

        assertEquals(0.6f, rect.width, 0.0001f)
        assertEquals(0.5f, rect.height, 0.0001f)
    }
}
