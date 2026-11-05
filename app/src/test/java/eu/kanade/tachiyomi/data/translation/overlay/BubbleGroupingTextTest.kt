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

    @Test
    fun `overlapping boxes with the same text are one detection`() {
        val boxes = listOf(
            box("THE NEXT DAY, EARLY MORNING translated in full", "THE NEXT DAY, EARLY MORNING"),
            box("EARLY MORNING short", "EARLY MORNING"),
        )
        val pixels = listOf(PixelRect(100, 100, 400, 200), PixelRect(150, 130, 350, 170))

        assertEquals(listOf(0), BubbleGrouping.withoutDuplicates(boxes, pixels))
        // Whichever order they arrive in, the more complete translation survives
        assertEquals(listOf(1), BubbleGrouping.withoutDuplicates(boxes.reversed(), pixels.reversed()))
    }

    @Test
    fun `the same words in two different places are both kept`() {
        val boxes = listOf(
            box("first", "I WILL JUST WATCH YOU OPERATE"),
            box("second", "I WILL JUST WATCH YOU OPERATE"),
        )
        val pixels = listOf(PixelRect(0, 0, 100, 50), PixelRect(0, 900, 100, 950))

        assertEquals(listOf(0, 1), BubbleGrouping.withoutDuplicates(boxes, pixels))
    }

    @Test
    fun `different text in the same place is kept`() {
        val boxes = listOf(box("one", "HELLO THERE MY FRIEND"), box("two", "HOW ARE YOU TODAY"))
        val pixels = listOf(PixelRect(0, 0, 100, 50), PixelRect(0, 0, 100, 50))

        assertEquals(listOf(0, 1), BubbleGrouping.withoutDuplicates(boxes, pixels))
    }
}
