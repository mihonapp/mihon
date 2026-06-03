package tachiyomi.core.common.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TallImageSplitCalculatorTest {

    @Test
    fun `does not split when aspect ratio is tall but computed split count is one`() {
        assertFalse(
            TallImageSplitCalculator.shouldSplit(
                imageWidth = 1024,
                imageHeight = 4385,
                optimalImageHeight = 4386,
            ),
        )
    }

    @Test
    fun `splits when aspect ratio is tall and computed split count is greater than one`() {
        assertTrue(
            TallImageSplitCalculator.shouldSplit(
                imageWidth = 1024,
                imageHeight = 4385,
                optimalImageHeight = 4384,
            ),
        )
    }

    @Test
    fun `calculate part count rounds boundary correctly`() {
        assertEquals(1, TallImageSplitCalculator.calculatePartCount(imageHeight = 4384, optimalImageHeight = 4384))
        assertEquals(2, TallImageSplitCalculator.calculatePartCount(imageHeight = 4385, optimalImageHeight = 4384))
    }
}
