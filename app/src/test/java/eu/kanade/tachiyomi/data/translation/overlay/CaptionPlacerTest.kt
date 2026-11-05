package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptionPlacerTest {

    private val page = PixelRect(0, 0, 800, 6000)

    private fun inside(r: PixelRect) =
        r.left >= page.left && r.top >= page.top && r.right <= page.right && r.bottom <= page.bottom

    private fun overlaps(a: PixelRect, b: PixelRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test
    fun `caption goes right below the original text`() {
        val box = PixelRect(300, 1000, 500, 1100)
        val caption = CaptionPlacer.place(box, page, emptyList())
        assertTrue(caption.top >= box.bottom)
        assertTrue(caption.top - box.bottom < 40)
        assertTrue(inside(caption))
        assertFalse(overlaps(caption, box))
    }

    @Test
    fun `caption is centred under the text`() {
        val box = PixelRect(300, 1000, 500, 1100)
        val caption = CaptionPlacer.place(box, page, emptyList())
        assertTrue(kotlin.math.abs(caption.centerX - box.centerX) <= 1)
    }

    @Test
    fun `caption goes above when the page ends below the text`() {
        val box = PixelRect(300, 5850, 500, 5950)
        val caption = CaptionPlacer.place(box, page, emptyList())
        assertTrue(caption.bottom <= box.top)
        assertTrue(inside(caption))
    }

    @Test
    fun `second caption does not cover the first`() {
        val first = CaptionPlacer.place(PixelRect(300, 1000, 500, 1100), page, emptyList())
        val second = CaptionPlacer.place(PixelRect(300, 1105, 500, 1205), page, listOf(first))
        assertFalse(overlaps(first, second))
        assertTrue(inside(second))
    }

    @Test
    fun `caption stays inside the page at the edges`() {
        val left = CaptionPlacer.place(PixelRect(0, 1000, 120, 1100), page, emptyList())
        val right = CaptionPlacer.place(PixelRect(700, 1000, 800, 1100), page, emptyList())
        assertTrue(inside(left))
        assertTrue(inside(right))
    }

    @Test
    fun `tiny page still gives a caption inside it`() {
        val small = PixelRect(0, 0, 100, 150)
        val caption = CaptionPlacer.place(PixelRect(10, 10, 60, 40), small, emptyList())
        assertTrue(caption.left >= 0 && caption.top >= 0 && caption.right <= 100 && caption.bottom <= 150)
        assertEquals(false, caption.isEmpty)
    }
}
