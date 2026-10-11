package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BubbleFinderTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private class Page(val width: Int, val height: Int, background: Int) {
        val pixels = IntArray(width * height) { background }
        val bounds = PixelRect(0, 0, width, height)

        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, fill: Int, outline: Int, outlineWidth: Int = 3) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val outer = sq((x - cx).toDouble() / rx) + sq((y - cy).toDouble() / ry)
                    val inner = sq((x - cx).toDouble() / (rx - outlineWidth)) +
                        sq((y - cy).toDouble() / (ry - outlineWidth))
                    if (inner <= 1.0) {
                        pixels[y * width + x] = fill
                    } else if (outer <= 1.0) {
                        pixels[y * width + x] = outline
                    }
                }
            }
        }

        /** Draws a checkerboard of dark pixels inside [rect], like text strokes. */
        fun text(rect: PixelRect, color: Int) {
            for (y in rect.top until rect.bottom) {
                for (x in rect.left until rect.right) {
                    if ((x + y) % 2 == 0) pixels[y * width + x] = color
                }
            }
        }

        fun window(rect: PixelRect): IntArray {
            val out = IntArray(rect.width * rect.height)
            for (y in 0 until rect.height) {
                for (x in 0 until rect.width) out[y * rect.width + x] = pixels[(rect.top + y) * width + rect.left + x]
            }
            return out
        }

        private fun sq(v: Double) = v * v
    }

    private fun find(
        page: Page,
        box: PixelRect,
        config: BubbleFinder.Config = BubbleFinder.Config(),
        traceBubble: Boolean = true,
    ): BubbleRegion {
        val window = BubbleFinder.windowFor(box, page.bounds)
        return BubbleFinder.find(page.window(window), window, box, config, traceBubble)
    }

    @Test
    fun `traces a white bubble up to its outline`() {
        val page = Page(200, 200, 0xFF808080.toInt())
        page.ellipse(100, 100, 60, 45, white, black)
        val box = PixelRect(65, 78, 135, 122)
        page.text(PixelRect(70, 82, 130, 118), black)

        val region = find(page, box)

        assertTrue(region.fromFloodFill)
        assertEquals(white, region.background)
        // Inside the bubble but away from the text
        assertTrue(region.contains(100, 60))
        assertTrue(region.contains(48, 100))
        // Outline and artwork are untouched
        assertFalse(region.contains(40, 100))
        assertFalse(region.contains(10, 100))
        assertFalse(region.contains(100, 52))
    }

    @Test
    fun `old text is fully covered including letters that stick out of the box`() {
        val page = Page(200, 200, 0xFF808080.toInt())
        page.ellipse(100, 100, 60, 45, white, black)
        val box = PixelRect(65, 78, 135, 122)
        page.text(PixelRect(62, 76, 138, 124), black) // 3px bigger than the box on every side

        val region = find(page, box)

        for (y in 76 until 124) {
            for (x in 62 until 138) {
                if ((x + y) % 2 == 0) assertTrue(region.contains(x, y), "text pixel $x,$y left uncovered")
            }
        }
    }

    @Test
    fun `text rectangle is larger than the box and stays inside the bubble`() {
        val page = Page(200, 200, 0xFF808080.toInt())
        page.ellipse(100, 100, 60, 45, white, black)
        val box = PixelRect(80, 90, 120, 110)
        page.text(PixelRect(82, 92, 118, 108), black)

        val region = find(page, box)
        val r = region.textRect

        assertTrue(region.fromFloodFill)
        assertTrue(r.width > box.width, "expected wider than the box, got ${r.width}")
        assertTrue(r.height > box.height, "expected taller than the box, got ${r.height}")
        assertTrue(region.contains(r.left, r.top))
        assertTrue(region.contains(r.right - 1, r.top))
        assertTrue(region.contains(r.left, r.bottom - 1))
        assertTrue(region.contains(r.right - 1, r.bottom - 1))
    }

    @Test
    fun `gray bubble keeps its colour`() {
        val gray = 0xFFC8C8C8.toInt()
        val page = Page(200, 200, white)
        page.ellipse(100, 100, 60, 45, gray, black)
        val box = PixelRect(65, 78, 135, 122)
        page.text(PixelRect(70, 82, 130, 118), black)

        val region = find(page, box)

        assertTrue(region.fromFloodFill)
        assertEquals(gray, region.background)
    }

    @Test
    fun `leaking fill falls back to the padded box`() {
        // No outline: the white fill would flood the whole page
        val page = Page(200, 200, white)
        val box = PixelRect(80, 90, 120, 110)
        page.text(PixelRect(82, 92, 118, 108), black)

        val region = find(page, box)

        assertFalse(region.fromFloodFill)
        assertTrue(region.contains(100, 100))
        assertFalse(region.contains(20, 20))
        assertFalse(region.contains(100, 60))
        // The text still has room to be drawn inside the padded box
        assertTrue(region.textRect.width in 30..60)
    }

    @Test
    fun `colour tolerance ignores jpeg style noise`() {
        val page = Page(200, 200, 0xFF808080.toInt())
        page.ellipse(100, 100, 60, 45, white, black)
        for (i in page.pixels.indices) {
            if (page.pixels[i] == white && i % 7 == 0) page.pixels[i] = 0xFFF2F2F2.toInt()
        }
        val box = PixelRect(65, 78, 135, 122)

        val region = find(page, box)

        assertTrue(region.fromFloodFill)
        assertTrue(region.contains(48, 100))
        assertFalse(region.contains(40, 100))
    }

    @Test
    fun `open text is erased even when the box cuts through the letters`() {
        val page = Page(300, 200, white)
        page.text(PixelRect(40, 80, 260, 110), black) // the real text line
        val box = PixelRect(40, 90, 260, 100) // the model's box only covers the middle of it

        val region = find(page, box)

        assertFalse(region.fromFloodFill)
        assertTrue(region.contains(150, 81))
        assertTrue(region.contains(150, 108))
        val fill = region.fill!!
        // Letter pixels are replaced with the page colour
        val index = (100 - region.window.top) * region.window.width + (150 - region.window.left)
        assertEquals(white, fill[index])
    }

    @Test
    fun `caption boxes erase glyphs without repainting the sign or nearby art`() {
        val gray = 0xFF777777.toInt()
        val page = Page(200, 180, gray)
        for (y in 50 until 110) for (x in 40 until 160) page.pixels[y * page.width + x] = white
        val box = PixelRect(70, 70, 130, 90)
        page.text(PixelRect(76, 75, 124, 85), black)

        val region = find(page, box, traceBubble = false)
        val signPixel = (60 - region.window.top) * region.window.width + (60 - region.window.left)
        val blankSignPixel = (60 - region.window.top) * region.window.width + (140 - region.window.left)
        val letterPixel = (80 - region.window.top) * region.window.width + (80 - region.window.left)

        assertFalse(region.fromFloodFill)
        assertFalse(region.mask[signPixel], "the surrounding artwork must not be repainted")
        assertFalse(region.mask[blankSignPixel], "the empty sign background must be preserved")
        assertTrue(region.mask[letterPixel], "the original glyph should be erased")
    }

    @Test
    fun `untraced speech bubbles over artwork are not safe to render in place`() {
        val page = Page(200, 160, white)
        // Busy artwork around the lettering: 4 px checker blocks
        for (y in 0 until 160) {
            for (x in 0 until 200) {
                if (((x / 4) + (y / 4)) % 2 == 0) page.pixels[y * 200 + x] = black
            }
        }
        val box = PixelRect(60, 70, 140, 100)
        page.text(PixelRect(70, 78, 130, 92), black)
        val region = find(page, box, traceBubble = false)

        assertFalse(region.fromFloodFill)
        assertFalse(region.flatSurroundings)
        assertFalse(BubbleGrouping.canRenderSafely(BoxKind.BUBBLE, region))
        assertTrue(BubbleGrouping.canRenderSafely(BoxKind.CAPTION, region))
    }

    @Test
    fun `untraced speech bubble on a flat page is safe to render`() {
        // Same situation as a bubble with a dashed outline: nothing to trace, but nothing to damage either
        val page = Page(200, 160, white)
        val box = PixelRect(60, 70, 140, 100)
        page.text(PixelRect(70, 78, 130, 92), black)
        val region = find(page, box)

        assertFalse(region.fromFloodFill)
        assertTrue(region.flatSurroundings)
        assertTrue(BubbleGrouping.canRenderSafely(BoxKind.BUBBLE, region))
    }

    @Test
    fun `lettering that cannot be erased is not drawn over`() {
        // A big solid logo: taller than any letter, so the eraser leaves it alone
        val page = Page(300, 200, white)
        for (y in 60 until 140) for (x in 60 until 240) page.pixels[y * 300 + x] = black
        val box = PixelRect(60, 60, 240, 140)
        val region = find(page, box, BubbleFinder.Config(maxLetterSide = 40), traceBubble = false)

        assertTrue(region.inkErasedShare < 0.6f)
        assertFalse(BubbleGrouping.canRenderSafely(BoxKind.CAPTION, region))
    }

    @Test
    fun `a box that reaches over the bubble outline does not repaint the outline`() {
        val page = Page(200, 200, 0xFF808080.toInt())
        page.ellipse(100, 100, 60, 45, white, black)
        // Wider than the bubble: the box edges lie outside the outline
        val box = PixelRect(35, 70, 165, 130)
        page.text(PixelRect(70, 85, 130, 115), black)

        val region = find(page, box, BubbleFinder.Config(maxLetterSide = 30))

        assertTrue(region.fromFloodFill)
        assertFalse(region.contains(40, 100), "left outline must stay")
        assertFalse(region.contains(159, 100), "right outline must stay")
        assertFalse(region.contains(30, 100), "page outside the bubble must stay")
        assertTrue(region.contains(100, 70), "bubble interior is still repainted")
        for (y in 85 until 115) {
            for (x in 70 until 130) {
                if ((x + y) % 2 == 0) assertTrue(region.contains(x, y), "text pixel $x,$y left uncovered")
            }
        }
    }

    @Test
    fun `ink growth stops at a clean gap`() {
        val page = Page(300, 200, white)
        page.text(PixelRect(40, 80, 260, 110), black)
        page.text(PixelRect(40, 140, 260, 170), black) // another line, 30 px lower
        val box = PixelRect(40, 90, 260, 100)

        val region = find(page, box)

        assertFalse(region.contains(150, 150))
    }

    @Test
    fun `only the letters are replaced, not the whole box`() {
        val page = Page(300, 200, white)
        page.text(PixelRect(100, 90, 200, 110), black)
        val box = PixelRect(60, 70, 240, 130) // much bigger than the text

        val region = find(page, box)

        // A spot inside the box but far from any letter is untouched
        val far = (75 - region.window.top) * region.window.width + (70 - region.window.left)
        assertFalse(region.mask[far])
        // A letter pixel is replaced
        val letter = (100 - region.window.top) * region.window.width + (150 - region.window.left)
        assertTrue(region.mask[letter])
    }

    @Test
    fun `light text on a gradient is removed without painting a flat block`() {
        val page = Page(300, 200, 0xFF000000.toInt())
        for (y in 0 until 200) {
            val blue = 100 + y / 2 // vertical gradient
            for (x in 0 until 300) page.pixels[y * 300 + x] = (0xFF shl 24) or (40 shl 16) or (120 shl 8) or blue
        }
        page.text(PixelRect(60, 90, 240, 110), white)
        val box = PixelRect(60, 88, 240, 112)

        val region = find(page, box)

        val fill = region.fill!!
        val w = region.window.width
        for (y in listOf(95, 100, 105)) {
            val index = (y - region.window.top) * w + (150 - region.window.left)
            if (!region.mask[index]) continue
            val expectedBlue = 100 + y / 2
            val blue = fill[index] and 0xFF
            assertTrue(abs(blue - expectedBlue) <= 6, "row $y: $blue vs $expectedBlue")
        }
        assertEquals(white, region.textColor)
    }

    @Test
    fun `artwork of the lettering colour is not erased`() {
        val page = Page(400, 300, white)
        // Letters: small dark groups in a row
        for (i in 0 until 8) page.text(PixelRect(100 + i * 25, 140, 100 + i * 25 + 14, 160), black)
        // A big dark blob (a face or hair) and a long thin line (a panel border) inside the same loose box
        page.text(PixelRect(110, 200, 190, 280), black)
        for (x in 60 until 340) page.pixels[120 * 400 + x] = black
        val box = PixelRect(60, 110, 340, 290)

        val config = BubbleFinder.Config(maxLetterSide = 28)
        val region = find(page, box, config)
        val w = region.window.width
        fun masked(x: Int, y: Int) = region.mask[(y - region.window.top) * w + (x - region.window.left)]

        assertTrue(masked(106, 150)) // a letter
        assertFalse(masked(150, 240)) // inside the blob
        assertFalse(masked(200, 120)) // on the long line
    }

    @Test
    fun `nothing is erased when nothing stands out from the background`() {
        val page = Page(200, 200, white)
        val box = PixelRect(60, 80, 140, 120)

        val region = find(page, box)

        assertFalse(region.fromFloodFill)
        assertTrue(region.mask.none { it })
    }

    @Test
    fun `dashes of a leaky bubble outline are not erased`() {
        val page = Page(400, 300, white)
        // Dashed ellipse: 10 px dashes with 10 px gaps, so the flood fill leaks through the gaps
        val cx = 200
        val cy = 150
        for (step in 0 until 360 step 3) {
            if ((step / 3) % 2 == 0) continue
            val rad = Math.toRadians(step.toDouble())
            val x = (cx + 150 * Math.cos(rad)).toInt()
            val y = (cy + 100 * Math.sin(rad)).toInt()
            page.text(PixelRect(x - 1, y - 1, x + 2, y + 2), black)
        }
        page.text(PixelRect(150, 140, 250, 160), black)
        val box = PixelRect(150, 135, 250, 165)

        val region = find(page, box, BubbleFinder.Config(maxLetterSide = 40))

        val w = region.window.width
        fun masked(x: Int, y: Int) = region.mask[(y - region.window.top) * w + (x - region.window.left)]
        assertTrue(masked(200, 150)) // text is erased
        val outline = (cx + 150) to cy
        assertFalse(masked(outline.first, outline.second))
    }

    @Test
    fun `boxes in the same bubble are grouped and boxes in other bubbles are not`() {
        val page = Page(400, 200, 0xFF808080.toInt())
        page.ellipse(100, 100, 70, 60, white, black)
        page.ellipse(300, 100, 70, 60, white, black)
        val boxes = listOf(
            PixelRect(80, 70, 120, 90), // bubble A, first line
            PixelRect(80, 100, 120, 125), // bubble A, second line
            PixelRect(280, 85, 320, 115), // bubble B
        )
        for (box in boxes) page.text(box, black)

        val regions = boxes.map { find(page, it) }
        val groups = BubbleGrouping.group(boxes, regions)

        assertEquals(listOf(listOf(0, 1), listOf(2)), groups)
    }

    @Test
    fun `overlapping open text extents are not treated as one bubble`() {
        val page = Page(260, 180, white)
        val boxes = listOf(PixelRect(60, 70, 160, 100), PixelRect(100, 70, 200, 100))
        boxes.forEach { page.text(it, black) }

        val regions = boxes.map { find(page, it) }
        val groups = BubbleGrouping.group(boxes, regions)

        assertTrue(regions.all { !it.fromFloodFill })
        assertEquals(listOf(listOf(0), listOf(1)), groups)
    }

    @Test
    fun `font fit finds the largest fitting size`() {
        val size = FontFit.largestFitting(8f, 80f) { it <= 23.3f }
        assertTrue(abs(size - 23.3f) <= 0.5f, "got $size")
        assertTrue(size <= 23.3f)
    }

    @Test
    fun `font fit returns the bounds when everything or nothing fits`() {
        assertEquals(80f, FontFit.largestFitting(8f, 80f) { true })
        assertEquals(8f, FontFit.largestFitting(8f, 80f) { false })
        assertEquals(10f, FontFit.largestFitting(10f, 10f) { true })
    }
}
