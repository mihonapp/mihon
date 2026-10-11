package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextRegionGrouperTest {

    private fun line(left: Int, top: Int, right: Int, bottom: Int, text: String) =
        DetectedTextRegion(PixelRect(left, top, right, bottom), text)

    @Test
    fun `the lines of one bubble become one region`() {
        val lines = listOf(
            line(200, 100, 500, 126, "AH, DR. LING!"),
            line(150, 132, 550, 158, "WHAT BRINGS YOU TO OUR"),
            line(180, 164, 520, 190, "GENERAL SURGERY OR?"),
        )

        val regions = TextRegionGrouper.group(lines)

        assertEquals(1, regions.size)
        assertEquals(PixelRect(150, 100, 550, 190), regions.single().bounds)
        assertEquals("AH, DR. LING! WHAT BRINGS YOU TO OUR GENERAL SURGERY OR?", regions.single().text)
    }

    @Test
    fun `bubbles that are far apart stay separate`() {
        val lines = listOf(
            line(200, 100, 500, 126, "FIRST BUBBLE LINE ONE"),
            line(200, 132, 500, 158, "FIRST BUBBLE LINE TWO"),
            line(200, 400, 500, 426, "SECOND BUBBLE LINE ONE"),
            line(200, 432, 500, 458, "SECOND BUBBLE LINE TWO"),
        )

        val regions = TextRegionGrouper.group(lines)

        assertEquals(2, regions.size)
        assertEquals(100, regions[0].bounds.top)
        assertEquals(400, regions[1].bounds.top)
    }

    @Test
    fun `side by side bubbles at the same height stay separate`() {
        val lines = listOf(
            line(20, 100, 220, 126, "LEFT BUBBLE TEXT"),
            line(450, 100, 650, 126, "RIGHT BUBBLE TEXT"),
        )

        assertEquals(2, TextRegionGrouper.group(lines).size)
    }

    @Test
    fun `lines of very different height are not merged`() {
        val lines = listOf(
            line(200, 100, 500, 126, "NORMAL DIALOGUE LINE"),
            line(200, 132, 500, 232, "HUGE"),
        )

        assertEquals(2, TextRegionGrouper.group(lines).size)
    }

    @Test
    fun `noise is dropped and dialogue is kept`() {
        assertTrue(TextRegionGrouper.isLikelyNoise("dsle"))
        assertTrue(TextRegionGrouper.isLikelyNoise("|| -- //"))
        assertTrue(TextRegionGrouper.isLikelyNoise("a1"))
        assertFalse(TextRegionGrouper.isLikelyNoise("WHAT?! WHY IS THIS KID STILL HERE?"))
        assertFalse(TextRegionGrouper.isLikelyNoise("No, BAM"))
    }

    @Test
    fun `regionsFrom keeps only grouped dialogue`() {
        val lines = listOf(
            line(200, 100, 500, 126, "WHAT?! WHY IS THIS KID"),
            line(200, 132, 500, 158, "STILL HERE?"),
            line(10, 900, 60, 920, "dsle"),
        )

        val regions = TextRegionGrouper.regionsFrom(lines)

        assertEquals(1, regions.size)
        assertEquals("WHAT?! WHY IS THIS KID STILL HERE?", regions.single().text)
    }
}
