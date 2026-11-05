package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TallPageCropperTest {

    @Test
    fun `ordinary page remains a single request region`() {
        assertEquals(listOf(TallPageCropper.Crop(0, 2400)), TallPageCropper.plan(700, 2400))
        assertEquals(listOf(TallPageCropper.Crop(0, 4375)), TallPageCropper.plan(1200, 4375))
    }

    @Test
    fun `very tall page is covered by bounded overlapping crops`() {
        val pageHeight = 4375
        val crops = TallPageCropper.plan(width = 700, height = pageHeight)

        assertEquals(2, crops.size)
        assertEquals(0, crops.first().top)
        assertEquals(pageHeight, crops.last().bottom)
        assertTrue(crops.all { it.height <= 2400 })
        assertTrue(crops.first().bottom > crops.last().top, "neighboring crops must overlap")
    }

    @Test
    fun `extremely tall page has no gaps between crops`() {
        val pageHeight = 9000
        val crops = TallPageCropper.plan(width = 700, height = pageHeight)

        assertTrue(crops.size > 2)
        assertEquals(0, crops.first().top)
        assertEquals(pageHeight, crops.last().bottom)
        assertTrue(crops.all { it.height <= 2400 })
        assertTrue(crops.zipWithNext().all { (first, second) -> first.bottom >= second.top })
    }

    @Test
    fun `crop-relative coordinates map to the original page`() {
        val mapped = TallPageCropper.mapToPage(
            box = TextBox(250, 100, 750, 900, BoxKind.BUBBLE, "Hello"),
            crop = TallPageCropper.Crop(1800, 4000),
            pageHeight = 4000,
        )

        assertEquals(588, mapped.yMin)
        assertEquals(100, mapped.xMin)
        assertEquals(863, mapped.yMax)
        assertEquals(900, mapped.xMax)
        assertEquals("Hello", mapped.text)
    }

    @Test
    fun `same bubble found in overlap is emitted once`() {
        val firstCrop = TallPageCropper.Crop(0, 2400)
        val secondCrop = TallPageCropper.Crop(1800, 4000)
        val detections = listOf(
            TallPageCropper.Detection(
                firstCrop,
                TextBox(800, 100, 900, 500, BoxKind.BUBBLE, "Hello, doctor!"),
            ),
            TallPageCropper.Detection(
                secondCrop,
                TextBox(55, 100, 164, 500, BoxKind.BUBBLE, "Hello doctor"),
            ),
        )

        val result = TallPageCropper.mapAndDeduplicate(detections, pageWidth = 700, pageHeight = 4000)

        assertEquals(1, result.size)
        assertEquals("Hello, doctor!", result.single().text)
    }

    @Test
    fun `overlap duplicates use source transcription when translations differ`() {
        val firstCrop = TallPageCropper.Crop(0, 2400)
        val secondCrop = TallPageCropper.Crop(1800, 4000)
        val detections = listOf(
            TallPageCropper.Detection(
                firstCrop,
                TextBox(800, 100, 900, 500, BoxKind.BUBBLE, "Translation A", "I will operate now."),
            ),
            TallPageCropper.Detection(
                secondCrop,
                TextBox(55, 100, 164, 500, BoxKind.BUBBLE, "Translation B", "I will operate now."),
            ),
        )

        val result = TallPageCropper.mapAndDeduplicate(detections, pageWidth = 700, pageHeight = 4000)

        assertEquals(1, result.size)
        assertEquals("Translation A", result.single().text)
        assertEquals("I will operate now.", result.single().sourceText)
    }

    @Test
    fun `identical words in different locations are not deduplicated`() {
        val crop = TallPageCropper.Crop(0, 4000)
        val detections = listOf(
            TallPageCropper.Detection(crop, TextBox(100, 100, 150, 300, BoxKind.BUBBLE, "Yes")),
            TallPageCropper.Detection(crop, TextBox(600, 100, 650, 300, BoxKind.BUBBLE, "Yes")),
        )

        val result = TallPageCropper.mapAndDeduplicate(detections, pageWidth = 700, pageHeight = 4000)

        assertEquals(2, result.size)
    }
}
