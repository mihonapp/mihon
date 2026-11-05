package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BubbleGroupingSafetyTest {

    private fun region(
        traced: Boolean,
        flat: Boolean = false,
        erased: Float = 1f,
    ) = BubbleRegion(
        window = PixelRect(0, 0, 10, 10),
        mask = BooleanArray(100),
        background = -1,
        textRect = PixelRect(0, 0, 10, 10),
        fromFloodFill = traced,
        flatSurroundings = flat,
        inkErasedShare = erased,
    )

    @Test
    fun `a traced bubble is always safe`() {
        assertTrue(BubbleGrouping.canRenderSafely(BoxKind.BUBBLE, region(traced = true, erased = 0f)))
    }

    @Test
    fun `an untraced bubble with mostly flat surroundings and erased lettering is safe`() {
        assertTrue(BubbleGrouping.canRenderSafely(BoxKind.BUBBLE, region(traced = false, flat = true, erased = 0.8f)))
    }

    @Test
    fun `an untraced bubble whose lettering was barely erased is not safe`() {
        assertFalse(BubbleGrouping.canRenderSafely(BoxKind.BUBBLE, region(traced = false, flat = true, erased = 0.65f)))
    }

    @Test
    fun `an untraced bubble over artwork is not safe`() {
        assertFalse(BubbleGrouping.canRenderSafely(BoxKind.BUBBLE, region(traced = false, flat = false, erased = 1f)))
    }

    @Test
    fun `a caption is safe once its lettering is erased`() {
        assertTrue(BubbleGrouping.canRenderSafely(BoxKind.CAPTION, region(traced = false, erased = 0.7f)))
        assertFalse(BubbleGrouping.canRenderSafely(BoxKind.CAPTION, region(traced = false, erased = 0.1f)))
    }
}
