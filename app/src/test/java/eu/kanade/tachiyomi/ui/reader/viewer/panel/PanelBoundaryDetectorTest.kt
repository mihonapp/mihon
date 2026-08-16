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
        // 30x20 page: a 10x10 black block on the left, a 10x10 black block on the right,
        // separated by a 10px-wide white gutter down the middle.
        val page = buffer(30, 20) { x, y ->
            val inLeftBlock = x in 0..9 && y in 0..19
            val inRightBlock = x in 20..29 && y in 0..19
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
        // Two 10x10 blocks joined by a bridge that is 4px tall, thick enough to survive erosion.
        val page = buffer(30, 20) { x, y ->
            val inLeftBlock = x in 0..9 && y in 0..19
            val inRightBlock = x in 20..29 && y in 0..19
            val inBridge = x in 10..19 && y in 8..11
            if (inLeftBlock || inRightBlock || inBridge) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(1, panels.size)
    }

    @Test
    fun `blocks joined only by a 1px border line separate into two panels`() {
        // Two 10x10 blocks joined by a single 1px-tall line (simulates a shared drawn border
        // with no real gutter), surrounded by white above and below the line.
        val page = buffer(30, 20) { x, y ->
            val inLeftBlock = x in 0..9 && y in 0..19
            val inRightBlock = x in 20..29 && y in 0..19
            val inThinBridge = x in 10..19 && y == 9
            if (inLeftBlock || inRightBlock || inThinBridge) black else white
        }

        val panels = PanelBoundaryDetector.detect(page)

        assertEquals(2, panels.size)
    }
}
