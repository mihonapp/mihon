package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelConfidenceTest {

    @Test
    fun `no detected panels is low confidence`() {
        assertTrue(PanelConfidence.isLowConfidence(emptyList()))
    }

    @Test
    fun `too many small scattered panels is low confidence`() {
        val scattered = (0 until 20).map { i ->
            val x = (i % 5) * 0.02f
            val y = (i / 5) * 0.02f
            PanelRect(x, y, x + 0.01f, y + 0.01f)
        }

        assertTrue(PanelConfidence.isLowConfidence(scattered))
    }

    @Test
    fun `a normal 2x2 grid covering most of the page is high confidence`() {
        val grid = listOf(
            PanelRect(0f, 0f, 0.48f, 0.48f),
            PanelRect(0.52f, 0f, 1f, 0.48f),
            PanelRect(0f, 0.52f, 0.48f, 1f),
            PanelRect(0.52f, 0.52f, 1f, 1f),
        )

        assertFalse(PanelConfidence.isLowConfidence(grid))
    }
}
