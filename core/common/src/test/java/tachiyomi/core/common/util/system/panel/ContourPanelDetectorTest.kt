package tachiyomi.core.common.util.system.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ContourPanelDetectorTest {

    private val detector = ContourPanelDetector()

    @Test
    fun `2x2 grid yields four panels at expected coordinates`() {
        val canvas = Canvas(600, 900, WHITE)
        val expected = listOf(
            PixelRect(20, 20, 290, 440),
            PixelRect(310, 20, 580, 440),
            PixelRect(20, 460, 290, 880),
            PixelRect(310, 460, 580, 880),
        )
        expected.forEach { canvas.border(it, BLACK) }

        val rects = detector.detect(canvas.toImage())

        assertEquals(4, rects.size)
        expected.forEach { assertHasRect(rects, it.normalised(600, 900)) }
    }

    @Test
    fun `narrow gutters well under one percent of the page keep panels separate`() {
        // Real albums downsampled to ~640 px wide have gutters of only a few pixels.
        val canvas = Canvas(640, 860, WHITE)
        val gutter = 4
        val cols = 3
        val rows = 4
        val cellW = (640 - 2 * 10 - (cols - 1) * gutter) / cols
        val cellH = (860 - 2 * 10 - (rows - 1) * gutter) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = 10 + c * (cellW + gutter)
                val top = 10 + r * (cellH + gutter)
                canvas.border(PixelRect(left, top, left + cellW, top + cellH), BLACK)
            }
        }

        assertEquals(rows * cols, detector.detect(canvas.toImage()).size)
    }

    @Test
    fun `separate shapes with overlapping bounding boxes merge into one region`() {
        // Borderless art: an L-shaped stroke with a drawing inside its bounding box but not touching it.
        val canvas = Canvas(600, 900, WHITE)
        canvas.fill(PixelRect(40, 40, 70, 500), BLACK)
        canvas.fill(PixelRect(40, 470, 560, 500), BLACK)
        canvas.fill(PixelRect(150, 120, 450, 400), BLACK)
        canvas.border(PixelRect(20, 600, 580, 880), BLACK)

        val rects = detector.detect(canvas.toImage())

        assertEquals(2, rects.size)
        assertHasRect(rects, PixelRect(40, 40, 560, 500).normalised(600, 900))
    }

    @Test
    fun `full-bleed art with white gutters falls back to a white background`() {
        // No page margin: the art runs to every edge, so the frame colour is art, not paper.
        val canvas = Canvas(600, 900, GREY)
        canvas.fill(PixelRect(295, 0, 305, 900), WHITE)
        canvas.fill(PixelRect(0, 445, 600, 455), WHITE)

        assertEquals(4, detector.detect(canvas.toImage()).size)
    }

    @Test
    fun `thin strip images are skipped`() {
        // Some releases cut pages into 1040x100 slivers; their bubble fragments are not panels.
        val canvas = Canvas(520, 50, WHITE)
        canvas.border(PixelRect(10, 5, 150, 45), BLACK)
        canvas.border(PixelRect(200, 5, 340, 45), BLACK)

        assertTrue(detector.detect(canvas.toImage()).isEmpty())
    }

    @Test
    fun `three stacked full-width strips yield three panels`() {
        val canvas = Canvas(600, 900, WHITE)
        canvas.border(PixelRect(20, 20, 580, 293), BLACK)
        canvas.border(PixelRect(20, 313, 580, 586), BLACK)
        canvas.border(PixelRect(20, 606, 580, 880), BLACK)

        assertEquals(3, detector.detect(canvas.toImage()).size)
    }

    @Test
    fun `tall left panel with two stacked right panels yields three panels`() {
        val canvas = Canvas(600, 900, WHITE)
        canvas.border(PixelRect(20, 20, 290, 880), BLACK)
        canvas.border(PixelRect(310, 20, 580, 440), BLACK)
        canvas.border(PixelRect(310, 460, 580, 880), BLACK)

        assertEquals(3, detector.detect(canvas.toImage()).size)
    }

    @Test
    fun `blank white page yields nothing`() {
        assertTrue(detector.detect(Canvas(600, 900, WHITE).toImage()).isEmpty())
    }

    @Test
    fun `single full-page border is rejected as degenerate`() {
        val canvas = Canvas(600, 900, WHITE)
        canvas.border(PixelRect(10, 10, 590, 890), BLACK)

        assertTrue(detector.detect(canvas.toImage()).isEmpty())
    }

    @Test
    fun `dark background with light-bordered panels yields two panels`() {
        val canvas = Canvas(600, 900, BLACK)
        canvas.border(PixelRect(20, 20, 290, 880), WHITE)
        canvas.border(PixelRect(310, 20, 580, 880), WHITE)

        val rects = detector.detect(canvas.toImage())

        assertEquals(2, rects.size)
        assertHasRect(rects, PixelRect(20, 20, 290, 880).normalised(600, 900))
        assertHasRect(rects, PixelRect(310, 20, 580, 880).normalised(600, 900))
    }

    @Test
    fun `bubble crossing a panel border into the gutter stays part of that panel`() {
        val canvas = Canvas(600, 900, WHITE)
        canvas.border(PixelRect(20, 20, 290, 440), BLACK)
        canvas.border(PixelRect(310, 20, 580, 440), BLACK)
        canvas.border(PixelRect(20, 460, 290, 880), BLACK)
        canvas.border(PixelRect(310, 460, 580, 880), BLACK)
        // Bubble straddles the top-left panel's right border and pokes 10 px into the 20 px gutter.
        canvas.fill(PixelRect(250, 100, 300, 140), BLACK)

        val rects = detector.detect(canvas.toImage())

        assertEquals(4, rects.size)
        val bubbleX = 275f / 600
        val bubbleY = 120f / 900
        assertEquals(1, rects.count { bubbleX in it.left..it.right && bubbleY in it.top..it.bottom })
    }

    @Test
    fun `borderless art between framed panels becomes a region of its own`() {
        val canvas = Canvas(640, 860, WHITE)
        canvas.border(PixelRect(20, 20, 620, 400), BLACK)
        canvas.border(PixelRect(20, 420, 300, 840), BLACK)
        canvas.border(PixelRect(420, 420, 620, 840), BLACK)
        // A bubble above a figure, drawn on the paper with no frame. Neither is big enough alone.
        canvas.fill(PixelRect(325, 440, 395, 500), BLACK)
        canvas.fill(PixelRect(315, 520, 405, 600), BLACK)
        // Page number in the bottom margin.
        canvas.fill(PixelRect(315, 848, 327, 856), BLACK)

        val rects = detector.detect(canvas.toImage())

        assertEquals(4, rects.size)
        assertHasRect(rects, PixelRect(315, 440, 405, 600).normalised(640, 860))
    }

    @Test
    fun `lettering of an unframed bubble across a gutter extends the panel it mostly covers`() {
        val canvas = Canvas(640, 860, WHITE)
        canvas.fill(PixelRect(20, 20, 310, 400), GREY)
        canvas.fill(PixelRect(330, 20, 620, 400), GREY)
        canvas.border(PixelRect(20, 420, 620, 840), BLACK)
        // A white bubble with no outline: it starts in the left panel, crosses the gutter and ends in the right one.
        canvas.fill(PixelRect(220, 40, 370, 100), WHITE)
        // Three lines of lettering, one small mark per letter.
        for (y in listOf(50, 65, 80)) {
            for (x in 230 until 360 step 8) canvas.fill(PixelRect(x, y, x + 5, y + 8), BLACK)
        }

        val rects = detector.detect(canvas.toImage())

        assertEquals(3, rects.size)
        assertHasRect(rects, PixelRect(20, 20, 359, 400).normalised(640, 860))
        assertHasRect(rects, PixelRect(330, 20, 620, 400).normalised(640, 860))
    }

    @Test
    fun `panels on either side of a slanted gutter stay separate`() {
        val canvas = Canvas(640, 860, WHITE)
        // The gutter rises from left to right, so the two bounding boxes overlap by a band.
        for (x in 20 until 620) {
            val gutterTop = 300 - (x - 20) * 100 / 600
            canvas.fill(PixelRect(x, 20, x + 1, gutterTop), GREY)
            canvas.fill(PixelRect(x, gutterTop + 12, x + 1, 840), GREY)
        }

        val rects = detector.detect(canvas.toImage())

        assertEquals(2, rects.size)
        assertHasRect(rects, PixelRect(20, 20, 620, 300).normalised(640, 860))
        assertHasRect(rects, PixelRect(20, 213, 620, 840).normalised(640, 860))

        // Each panel carries its slanted frame, clockwise from the top left.
        val upper = rects.minBy { it.top }
        val expected = listOf(20, 20, 620, 20, 620, 201, 20, 300)
        assertEquals(8, upper.corners.size)
        expected.forEachIndexed { i, pixel ->
            val normalised = pixel.toFloat() / if (i % 2 == 0) 640 else 860
            assertTrue(abs(upper.corners[i] - normalised) <= TOLERANCE, "corner value $i of ${upper.corners}")
        }
    }

    @Test
    fun `plain rectangular panels carry no frame corners`() {
        val canvas = Canvas(600, 900, WHITE)
        canvas.border(PixelRect(20, 20, 580, 440), BLACK)
        canvas.border(PixelRect(20, 460, 580, 880), BLACK)

        val rects = detector.detect(canvas.toImage())

        assertEquals(2, rects.size)
        assertTrue(rects.all { it.corners.isEmpty() })
    }

    @Test
    fun `panels joined by art crossing the gutter are cut apart`() {
        val canvas = Canvas(640, 860, WHITE)
        canvas.fill(PixelRect(20, 20, 315, 840), GREY)
        canvas.fill(PixelRect(325, 20, 620, 840), GREY)
        // A sound effect drawn across the gutter joins the two panels into one shape.
        canvas.fill(PixelRect(280, 300, 360, 420), BLACK)

        val rects = detector.detect(canvas.toImage())

        assertEquals(2, rects.size)
        assertTrue(rects.any { abs(it.left - 20f / 640) <= TOLERANCE && it.right < 0.6f }, "left panel in $rects")
        assertTrue(rects.any { abs(it.right - 620f / 640) <= TOLERANCE && it.left > 0.4f }, "right panel in $rects")
    }

    @Test
    fun `a bubble over the gutter stays whole in the panel that holds most of it`() {
        val canvas = Canvas(640, 860, WHITE)
        canvas.fill(PixelRect(20, 20, 620, 400), GREY)
        canvas.fill(PixelRect(20, 420, 620, 840), GREY)
        // An outlined bubble hanging from the upper panel across the gutter into the lower one.
        canvas.fill(PixelRect(250, 300, 390, 470), WHITE)
        canvas.border(PixelRect(250, 300, 390, 470), BLACK)

        val rects = detector.detect(canvas.toImage())

        assertEquals(2, rects.size)
        assertHasRect(rects, PixelRect(20, 20, 620, 470).normalised(640, 860))
        assertHasRect(rects, PixelRect(20, 411, 620, 840).normalised(640, 860))
    }

    @Test
    fun `a light band inside one panel is not a gutter`() {
        val canvas = Canvas(640, 860, WHITE)
        canvas.border(PixelRect(20, 20, 620, 840), BLACK)
        // Sparse art in a mostly white panel: no frame lines run along any band of paper.
        canvas.fill(PixelRect(60, 100, 200, 300), GREY)
        canvas.fill(PixelRect(420, 500, 580, 760), GREY)
        canvas.border(PixelRect(20, 850, 620, 858), BLACK)

        assertEquals(2, detector.detect(canvas.toImage()).size)
    }

    @Test
    fun `large all-ink images do not overflow and yield nothing`() {
        // Uniform black: the frame median is black, so nothing is ink.
        assertTrue(detector.detect(Canvas(1024, 1024, BLACK).toImage()).isEmpty())

        // White 1 px frame around a black interior: one 1022x1022 component, rejected as degenerate.
        val framed = Canvas(1024, 1024, WHITE)
        framed.fill(PixelRect(1, 1, 1023, 1023), BLACK)
        assertTrue(detector.detect(framed.toImage()).isEmpty())
    }

    private fun assertHasRect(rects: List<PanelRect>, expected: PanelRect) {
        assertTrue(
            rects.any {
                abs(it.left - expected.left) <= TOLERANCE &&
                    abs(it.top - expected.top) <= TOLERANCE &&
                    abs(it.right - expected.right) <= TOLERANCE &&
                    abs(it.bottom - expected.bottom) <= TOLERANCE
            },
            "Expected $expected within $TOLERANCE in $rects",
        )
    }

    /** Pixel rect with exclusive right/bottom edges. */
    private data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun normalised(width: Int, height: Int) = PanelRect(
            left.toFloat() / width,
            top.toFloat() / height,
            right.toFloat() / width,
            bottom.toFloat() / height,
        )
    }

    private class Canvas(val width: Int, val height: Int, background: Int) {
        private val pixels = ByteArray(width * height) { background.toByte() }

        fun fill(rect: PixelRect, color: Int) {
            for (y in rect.top until rect.bottom) {
                for (x in rect.left until rect.right) {
                    pixels[y * width + x] = color.toByte()
                }
            }
        }

        /** Draws a [BORDER] px outline just inside [rect]. */
        fun border(rect: PixelRect, color: Int) {
            fill(PixelRect(rect.left, rect.top, rect.right, rect.top + BORDER), color)
            fill(PixelRect(rect.left, rect.bottom - BORDER, rect.right, rect.bottom), color)
            fill(PixelRect(rect.left, rect.top, rect.left + BORDER, rect.bottom), color)
            fill(PixelRect(rect.right - BORDER, rect.top, rect.right, rect.bottom), color)
        }

        fun toImage() = GrayImage(width, height, pixels.copyOf())
    }

    private companion object {
        const val WHITE = 255
        const val BLACK = 0
        const val GREY = 110
        const val BORDER = 3
        const val TOLERANCE = 0.02f
    }
}
