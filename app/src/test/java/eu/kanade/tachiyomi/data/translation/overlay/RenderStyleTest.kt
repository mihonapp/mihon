package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenderStyleTest {

    @Test
    fun `known ids map to their values`() {
        val style = RenderStyle.from("serif", 80, "below")
        assertEquals(RenderStyle.Font.SERIF, style.font)
        assertEquals(80, style.sizePercent)
        assertEquals(RenderStyle.Placement.BELOW, style.placement)
    }

    @Test
    fun `unknown or missing ids fall back to the defaults`() {
        val style = RenderStyle.from(null, 100, "nonsense")
        assertEquals(RenderStyle.Font.BOLD, style.font)
        assertEquals(RenderStyle.Placement.REPLACE, style.placement)
        assertEquals(RenderStyle.Font.BOLD, RenderStyle.Font.fromId("???"))
    }

    @Test
    fun `size is kept within the allowed range`() {
        assertEquals(RenderStyle.MIN_SIZE_PERCENT, RenderStyle.from("bold", 10, "replace").sizePercent)
        assertEquals(RenderStyle.MAX_SIZE_PERCENT, RenderStyle.from("bold", 500, "replace").sizePercent)
    }

    @Test
    fun `default style matches the old look`() {
        assertEquals(RenderStyle.Font.BOLD, RenderStyle.DEFAULT.font)
        assertEquals(100, RenderStyle.DEFAULT.sizePercent)
        assertEquals(RenderStyle.Placement.REPLACE, RenderStyle.DEFAULT.placement)
    }
}
