package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextEraserTest {

    @Test
    fun `erases multiple high contrast colors in outlined lettering`() {
        val width = 100
        val height = 60
        val background = 0xFF26364A.toInt()
        val cyan = 0xFF00D8FF.toInt()
        val white = 0xFFFFFFFF.toInt()
        val pixels = IntArray(width * height) { background }
        val area = PixelRect(25, 18, 75, 42)

        // Two separate, letter-sized parts of a multicolour word: one cyan and one white.
        for (y in 23 until 37) for (x in 32 until 42) pixels[y * width + x] = cyan
        for (y in 23 until 37) for (x in 56 until 66) pixels[y * width + x] = white

        val detectedColor = TextEraser.detectTextColor(pixels, width, area, background)!!
        val result = TextEraser.erase(
            pixels = pixels,
            w = width,
            h = height,
            area = area,
            background = background,
            textColor = detectedColor,
            dilation = 0,
            maxLetterSide = 20,
            keepArea = area,
        )

        assertTrue(result.mask[30 * width + 36], "cyan glyph pixels should be erased")
        assertTrue(result.mask[30 * width + 60], "white outline/fill pixels should be erased")
        assertFalse(result.mask[10 * width + 10], "pixels outside the lettering box must be preserved")
    }
}
