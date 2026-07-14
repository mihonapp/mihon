package eu.kanade.tachiyomi.ui.browse.migration.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrateSearchScreenModelTest {

    @Test
    fun `chapterCountDelta returns positive delta when candidate has more chapters`() {
        assertEquals(12, chapterCountDelta(fromCount = 40, candidateCount = 52))
    }

    @Test
    fun `chapterCountDelta returns zero when counts are equal`() {
        assertEquals(0, chapterCountDelta(fromCount = 40, candidateCount = 40))
    }

    @Test
    fun `chapterCountDelta returns negative delta when candidate has fewer chapters`() {
        assertEquals(-32, chapterCountDelta(fromCount = 40, candidateCount = 8))
    }

    @Test
    fun `chapterCountDelta works when fromCount is zero`() {
        assertEquals(5, chapterCountDelta(fromCount = 0, candidateCount = 5))
    }

    @Test
    fun `shouldRequestChapterCountDelta returns true when all conditions met`() {
        assertTrue(
            shouldRequestChapterCountDelta(
                mangaId = 2L,
                fromId = 1L,
                enabled = true,
                alreadyRequested = false,
            ),
        )
    }

    @Test
    fun `shouldRequestChapterCountDelta returns false when pref disabled`() {
        assertFalse(
            shouldRequestChapterCountDelta(
                mangaId = 2L,
                fromId = 1L,
                enabled = false,
                alreadyRequested = false,
            ),
        )
    }

    @Test
    fun `shouldRequestChapterCountDelta returns false when manga is the from-manga`() {
        assertFalse(
            shouldRequestChapterCountDelta(
                mangaId = 1L,
                fromId = 1L,
                enabled = true,
                alreadyRequested = false,
            ),
        )
    }

    @Test
    fun `shouldRequestChapterCountDelta returns false when already requested`() {
        assertFalse(
            shouldRequestChapterCountDelta(
                mangaId = 2L,
                fromId = 1L,
                enabled = true,
                alreadyRequested = true,
            ),
        )
    }
}
