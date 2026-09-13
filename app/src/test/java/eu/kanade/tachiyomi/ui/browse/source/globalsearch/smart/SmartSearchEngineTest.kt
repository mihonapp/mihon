package eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class QueryNormalizerTest {

    @Test
    fun `lowercases and collapses whitespace`() {
        assertEquals("one punch man", QueryNormalizer.normalize("  One   PUNCH   Man  "))
    }

    @Test
    fun `strips accents`() {
        assertEquals("shounen", QueryNormalizer.normalize("shōnen"))
        assertEquals("berserk", QueryNormalizer.normalize("bersèrk"))
    }

    @Test
    fun `removes punctuation`() {
        assertEquals("dragon ball z", QueryNormalizer.normalize("Dragon Ball Z!!"))
        assertEquals("manga dex test", QueryNormalizer.normalize("manga-dex_test"))
    }

    @Test
    fun `expands aliases`() {
        assertEquals("shounen", QueryNormalizer.normalize("shonen"))
        assertEquals("solo leveling", QueryNormalizer.normalize("solo leve rule"))
    }

    @Test
    fun `tokenizes normalized query`() {
        assertEquals(listOf("one", "punch", "man"), QueryNormalizer.tokenize("one punch man"))
    }

    @Test
    fun `levenshtein caps distance`() {
        assertEquals(0, QueryNormalizer.levenshtein("naruto", "naruto"))
        assertEquals(2, QueryNormalizer.levenshtein("nabuto", "naruto", 2))
        assertEquals(3, QueryNormalizer.levenshtein("a", "xyz", 2))
    }
}

class FuzzyMatcherTest {

    @Test
    fun `exact match scores highest`() {
        assertEquals(1f, FuzzyMatcher.score("naruto", "naruto"), 0.001f)
    }

    @Test
    fun `typo tolerant scoring`() {
        val score = FuzzyMatcher.score("naruto shipudden", "naruto shippuden")
        assertTrue(score > 0.5f, "expected its score to be above 0.5 but was $score")
    }

    @Test
    fun `partial word matches`() {
        val score = FuzzyMatcher.score("dragonball z", "dragon ball z")
        assertTrue(score > 0.5f, "expected its score to be above 0.5 but was $score")
    }
}

class SmartSearchEngineTest {

    private fun manga(id: Long, title: String, source: Long = 1L) =
        Manga.create().copy(
            id = id,
            source = source,
            url = "/title/$id",
            title = title,
        )

    @Test
    fun `merges duplicate titles across sources`() {
        val results = SmartSearchEngine.merge(
            normalizedQuery = "naruto",
            rawResults = listOf(
                manga(1, "Naruto", source = 1),
                manga(2, "Naruto", source = 2),
                manga(3, "Naruto Shippuden", source = 1),
            ),
        )
        val naruto = results.first { it.title == "Naruto" }
        assertEquals(1, naruto.alternatives.size)
        assertEquals(2, results.size)
    }

    @Test
    fun `filters low scoring results below minimum`() {
        val results = SmartSearchEngine.merge(
            normalizedQuery = "one piece",
            rawResults = listOf(
                manga(1, "One Piece"),
                manga(2, "Totally Unrelated Title"),
            ),
        )
        assertEquals(1, results.size)
        assertEquals("One Piece", results.first().title)
    }

    @Test
    fun `ranks by score descending`() {
        val results = SmartSearchEngine.merge(
            normalizedQuery = "berserk",
            rawResults = listOf(
                manga(1, "Berserk"),
                manga(2, "Berserk of Gluttony"),
            ),
        )
        assertTrue(results.isNotEmpty())
        assertEquals("Berserk", results.first().title)
    }

    @Test
    fun `produces suggestions with no results`() {
        val suggestions = SmartSearchEngine.suggest("levling")
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { "level" in it })
    }

    @Test
    fun `returns empty when query blank`() {
        assertTrue(SmartSearchEngine.suggest("   ").isEmpty())
        assertTrue(SmartSearchEngine.merge("", listOf(manga(1, "Any"))).isEmpty())
    }
}
