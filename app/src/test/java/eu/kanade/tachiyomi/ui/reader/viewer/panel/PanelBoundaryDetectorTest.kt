package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelBoundaryDetectorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun buffer(width: Int, height: Int, fill: (x: Int, y: Int) -> Int): PixelBuffer {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = fill(x, y)
            }
        }
        return PixelBuffer(width, height, pixels)
    }

    @Test
    fun `two blocks separated by a wide gutter detect as two panels`() {
        // 36x26 page with a 3px background margin: a 10x20 black block on the left, a
        // 10x20 black block on the right, separated by a 10px-wide white gutter down the
        // middle, none of the blocks touching the buffer edge.
        val page = buffer(36, 26) { x, y ->
            val inLeftBlock = x in 3..12 && y in 3..22
            val inRightBlock = x in 23..32 && y in 3..22
            if (inLeftBlock || inRightBlock) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }

    @Test
    fun `a single large block detects as one panel`() {
        val page = buffer(30, 20) { x, y -> if (x in 2..27 && y in 2..17) black else white }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size)
    }

    @Test
    fun `a blank page detects no panels`() {
        val page = buffer(30, 20) { _, _ -> white }

        val panels = PanelBoundaryDetector.detect(page)

        assertTrue(panels.isEmpty())
    }

    @Test
    fun `blocks joined by a thick bridge stay merged as one panel`() {
        // Same two blocks as above, joined by a bridge that is 4px tall, thick enough to
        // survive erosion.
        val page = buffer(36, 26) { x, y ->
            val inLeftBlock = x in 3..12 && y in 3..22
            val inRightBlock = x in 23..32 && y in 3..22
            val inBridge = x in 13..22 && y in 11..14
            if (inLeftBlock || inRightBlock || inBridge) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size)
    }

    @Test
    fun `blocks joined only by a 1px border line separate into two panels`() {
        // Same two blocks, joined by a single 1px-tall line (simulates a shared drawn
        // border with no real gutter), surrounded by background above and below the line.
        val page = buffer(36, 26) { x, y ->
            val inLeftBlock = x in 3..12 && y in 3..22
            val inRightBlock = x in 23..32 && y in 3..22
            val inThinBridge = x in 13..22 && y == 12
            if (inLeftBlock || inRightBlock || inThinBridge) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }

    @Test
    fun `a thin-framed panel with a white interior is still detected`() {
        // A 40x40 page: a 3px-thick black rectangular frame outline (outer edge 5..34,
        // inner edge 8..31) around a white interior, on a white background — the
        // realistic manga morphology (panels are usually white/colored interiors bounded
        // by a thin drawn frame, not solid filled blocks like the other fixtures above).
        val page = buffer(40, 40) { x, y ->
            val inOuter = x in 5..34 && y in 5..34
            val inInner = x in 8..31 && y in 8..31
            if (inOuter && !inInner) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertTrue(panels.isNotEmpty(), "expected the frame outline to survive erosion as at least one panel")
        val panel = panels.first()
        // Bounding box should roughly match the frame's extent (5..34 out of 40, i.e. ~0.12..0.85),
        // allowing some slack since erosion shrinks the surviving pixels inward by ~1px.
        assertTrue(panel.left in 0.0f..0.3f, "left was ${panel.left}")
        assertTrue(panel.top in 0.0f..0.3f, "top was ${panel.top}")
        assertTrue(panel.right in 0.7f..1.0f, "right was ${panel.right}")
        assertTrue(panel.bottom in 0.7f..1.0f, "bottom was ${panel.bottom}")
    }
}
