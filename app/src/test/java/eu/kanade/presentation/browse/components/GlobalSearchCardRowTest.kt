package eu.kanade.presentation.browse.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobalSearchCardRowTest {

    @Test
    fun `chapterCountDeltaLabel returns plus-prefixed string for positive delta`() {
        assertEquals("+12", chapterCountDeltaLabel(12))
    }

    @Test
    fun `chapterCountDeltaLabel returns plus-prefixed string for another positive delta`() {
        assertEquals("+5", chapterCountDeltaLabel(5))
    }

    @Test
    fun `chapterCountDeltaLabel returns equals sign for zero delta`() {
        assertEquals("=", chapterCountDeltaLabel(0))
    }

    @Test
    fun `chapterCountDeltaLabel returns string representation for negative delta`() {
        assertEquals("-3", chapterCountDeltaLabel(-3))
    }

    @Test
    fun `chapterCountDeltaLabel returns string representation for another negative delta`() {
        assertEquals("-1", chapterCountDeltaLabel(-1))
    }
}
