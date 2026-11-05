package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxFilterTest {

    private fun box(source: String) = TextBox(0, 0, 10, 10, BoxKind.BUBBLE, "translated", source)

    @Test
    fun `a logo and a watermark in another script are dropped`() {
        val boxes = listOf(
            box("大医凌然 Dr.LING RAN"),
            box("WAIT, THE SYSTEM GIVES A CHEST"),
            box("快看! 快看漫画"),
            box("NO RESPONSE"),
            box("FINE, I WON'T ARGUE"),
            box("HEADING TO GENERAL SURGERY"),
        )

        val kept = BoxFilter.withoutScriptOutliers(boxes)

        assertEquals(4, kept.size)
        assertTrue(kept.none { BoxFilter.hasCjk(it.sourceText) })
    }

    @Test
    fun `a page that is mostly CJK keeps everything`() {
        val boxes = listOf(box("你好"), box("再见"), box("谢谢"), box("OK"))

        assertEquals(boxes, BoxFilter.withoutScriptOutliers(boxes))
    }

    @Test
    fun `a page with too few boxes to tell keeps everything`() {
        val boxes = listOf(box("HELLO THERE"), box("你好"))

        assertEquals(boxes, BoxFilter.withoutScriptOutliers(boxes))
    }

    @Test
    fun `boxes without a transcription do not count`() {
        val boxes = listOf(box(""), box(""), box("HELLO"), box("WORLD"))

        assertEquals(boxes, BoxFilter.withoutScriptOutliers(boxes))
    }

    @Test
    fun `kana and hangul count as CJK, plain letters do not`() {
        assertTrue(BoxFilter.hasCjk("こんにちは"))
        assertTrue(BoxFilter.hasCjk("안녕하세요"))
        assertFalse(BoxFilter.hasCjk("Dr. Ling Ran"))
        assertFalse(BoxFilter.hasCjk("مرحبا"))
    }
}
