package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.overlay.BoxKind
import eu.kanade.tachiyomi.data.translation.overlay.TextBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationLogBufferTest {

    @Test
    fun `every line starts with the time since the start`() {
        var now = 1_000L
        val buffer = TranslationLogBuffer(clock = { now })

        buffer.add("first")
        now += 65_432
        buffer.add("second")

        assertEquals("[00:00.000] first\n[01:05.432] second\n", buffer.text())
    }

    @Test
    fun `the api key never appears in the log`() {
        val buffer = TranslationLogBuffer(secrets = listOf("AIzaSyExampleKey1234"))

        buffer.add("request to https://example.test?key=AIzaSyExampleKey1234 failed")

        assertFalse(buffer.text().contains("AIzaSyExampleKey1234"))
        assertTrue(buffer.text().contains("key=***"))
    }

    @Test
    fun `short secrets are not replaced`() {
        val buffer = TranslationLogBuffer(secrets = listOf("abc"))

        buffer.add("abc stays")

        assertTrue(buffer.text().contains("abc stays"))
    }

    @Test
    fun `continuation lines are indented under the first line`() {
        val buffer = TranslationLogBuffer(clock = { 0L })

        buffer.add("one\ntwo")

        assertEquals("[00:00.000] one\n            two\n", buffer.text())
    }

    @Test
    fun `the log stops growing at its limit`() {
        val buffer = TranslationLogBuffer(maxChars = 120, clock = { 0L })

        repeat(50) { buffer.add("a fairly long line of text number $it") }
        val size = buffer.text().length
        buffer.add("ignored")

        assertTrue(size <= 200)
        assertTrue(buffer.text().contains("the log is full"))
        assertFalse(buffer.text().contains("ignored"))
    }

    @Test
    fun `nothing is built while no log is active`() {
        TranslationLog.stop()
        var built = false

        TranslationLog.step {
            built = true
            "message"
        }

        assertFalse(built)
        assertEquals(null, TranslationLog.text())
    }

    @Test
    fun `an active log records steps and hides the key`() {
        TranslationLog.start(listOf("AIzaSyExampleKey1234"))
        try {
            TranslationLog.step { "using AIzaSyExampleKey1234" }
            val text = requireNotNull(TranslationLog.text())

            assertTrue(text.contains("using ***"))
        } finally {
            TranslationLog.stop()
        }
    }

    @Test
    fun `an error is described with its class and message`() {
        val text = TranslationLog.describe(IllegalStateException("boom"), maxFrames = 0)

        assertEquals("IllegalStateException: boom", text)
    }

    @Test
    fun `a box is described with its grid position and texts`() {
        val box = TextBox(10, 20, 30, 40, BoxKind.CAPTION, "translated", "original")

        assertEquals(
            "caption [y 10-30, x 20-40] source \"original\" -> \"translated\"",
            TranslationLog.describe(box),
        )
    }

    @Test
    fun `clip puts text on one line and shortens it`() {
        assertEquals("a b", TranslationLog.clip("a\n  b"))
        assertEquals("abcde…", TranslationLog.clip("abcdefgh", max = 5))
    }
}
