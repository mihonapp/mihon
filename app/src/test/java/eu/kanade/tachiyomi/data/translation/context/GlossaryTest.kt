package eu.kanade.tachiyomi.data.translation.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlossaryTest {

    @Test
    fun `parses entries and ignores comments and blanks`() {
        val entries = Glossary.parse("# names\nKaito = Kaito\n\n火影 = Hokage\nbroken line\n= nothing\nx =")

        assertEquals(listOf(GlossaryEntry("Kaito", "Kaito"), GlossaryEntry("火影", "Hokage")), entries)
    }

    @Test
    fun `keeps equals signs in the translation and drops duplicates`() {
        val entries = Glossary.parse("a = b = c\nA = other")

        assertEquals(listOf(GlossaryEntry("a", "b = c")), entries)
    }

    @Test
    fun `limits entries and term length`() {
        val text = (1..300).joinToString("\n") { "s$it = t$it" }
        assertEquals(Glossary.MAX_ENTRIES, Glossary.parse(text).size)

        val long = Glossary.parse("${"x".repeat(200)} = y").single()
        assertEquals(Glossary.MAX_TERM_LENGTH, long.source.length)
    }

    @Test
    fun `format and parse round trip`() {
        val entries = listOf(GlossaryEntry("a", "b"), GlossaryEntry("c", "d"))
        assertEquals(entries, Glossary.parse(Glossary.format(entries)))
    }

    @Test
    fun `empty context gives empty prompt text`() {
        assertEquals("", PromptContext.build(emptyList(), listOf("  ", "")))
    }

    @Test
    fun `glossary and previous page are included`() {
        val text = PromptContext.build(listOf(GlossaryEntry("a", "b")), listOf("Hello\nthere", "Bye"))

        assertTrue("- a => b" in text)
        assertTrue("Hello there | Bye" in text)
    }

    @Test
    fun `previous page text is clipped to the end`() {
        val text = PromptContext.build(emptyList(), listOf("x".repeat(2000) + "END"))

        assertTrue(text.endsWith("END"))
        assertTrue(text.length < 800)
    }

    @Test
    fun `prompt builders append the context only when present`() {
        val plain = eu.kanade.tachiyomi.data.translation.NanoBananaClient.buildPrompt("French")
        val withContext = eu.kanade.tachiyomi.data.translation.NanoBananaClient.buildPrompt("French", "EXTRA")
        assertEquals("$plain\n\nEXTRA", withContext)

        val overlayPlain = eu.kanade.tachiyomi.data.translation.overlay.TextOverlayClient.buildPrompt("French", " ")
        assertEquals(eu.kanade.tachiyomi.data.translation.overlay.TextOverlayClient.buildPrompt("French"), overlayPlain)
    }
}
