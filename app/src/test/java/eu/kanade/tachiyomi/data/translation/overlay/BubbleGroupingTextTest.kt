package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BubbleGroupingTextTest {

    private fun box(text: String, source: String) = TextBox(0, 0, 10, 10, BoxKind.BUBBLE, text, source)

    @Test
    fun `the same sentence reported twice is drawn once`() {
        val boxes = listOf(
            box("first long translation of the sentence", "I WILL JUST WATCH YOU OPERATE"),
            box("second translation", "I WILL JUST WATCH YOU OPERATE"),
        )

        // The longer translation wins
        assertEquals("first long translation of the sentence", BubbleGrouping.mergedText(boxes))
    }

    @Test
    fun `a line that is part of the whole bubble text is dropped`() {
        val whole = box("whole bubble", "OH WOW NORMALLY I HEAR COMPLIMENTS ALL THE TIME BUT HEARING THEM IS DIFFERENT")
        val line = box("one line", "OH WOW NORMALLY I HEAR COMPLIMENTS ALL THE TIME")

        assertEquals("whole bubble", BubbleGrouping.mergedText(listOf(line, whole)))
        assertEquals("whole bubble", BubbleGrouping.mergedText(listOf(whole, line)))
    }

    @Test
    fun `different sentences are joined in order`() {
        val boxes = listOf(
            box("one", "HELLO THERE MY FRIEND"),
            box("two", "HOW ARE YOU TODAY"),
        )

        assertEquals("one two", BubbleGrouping.mergedText(boxes))
    }

    @Test
    fun `short repeated words are not treated as duplicates`() {
        assertFalse(BubbleGrouping.isDuplicate(box("a", "HEY"), box("b", "HEY")))
        assertTrue(BubbleGrouping.mergedText(listOf(box("a", "HEY"), box("b", "HEY"))).contains(" "))
    }

    @Test
    fun `boxes without a transcription are compared by their translation`() {
        val boxes = listOf(box("the very same translated text", ""), box("the very same translated text", ""))

        assertEquals("the very same translated text", BubbleGrouping.mergedText(boxes))
    }
}
