package tachiyomi.domain.reader.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.reader.model.ReadingModeAutoRule

class ReadingModeAutoRulesEvaluatorTest {

    private val webtoon = 4
    private val ltr = 1

    @Test
    fun emptyRules_returnsNull() {
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(
                rules = emptyList(),
                sourceId = 1L,
                genreTags = setOf("action"),
                categoryIds = setOf(1L),
            ),
        )
    }

    @Test
    fun sourceMatch_firstWins() {
        val rules = listOf(
            rule("a", webtoon, sourceIds = listOf(10L)),
            rule("b", ltr, sourceIds = listOf(10L)),
        )
        val match = ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 10L, genreTags = emptySet(), categoryIds = emptySet())
        assertEquals(webtoon, match?.readingModeFlag)
        assertEquals(1, match?.ruleNumber)
    }

    @Test
    fun emptySourceIds_matchesAnySource() {
        val rules = listOf(rule("a", webtoon))
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 99L, genreTags = emptySet(), categoryIds = emptySet())?.readingModeFlag,
        )
    }

    @Test
    fun sourceMismatch_skipsRule() {
        val rules = listOf(
            rule("a", webtoon, sourceIds = listOf(1L)),
            rule("b", ltr),
        )
        assertEquals(
            ltr,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 2L, genreTags = emptySet(), categoryIds = emptySet())?.readingModeFlag,
        )
    }

    @Test
    fun categoriesAllOf_requiresEvery() {
        val rules = listOf(
            rule("a", webtoon, categoriesAllOf = listOf(1L, 2L)),
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(1L)),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(1L, 2L))?.readingModeFlag,
        )
    }

    @Test
    fun categoriesAnyOf_requiresOne() {
        val rules = listOf(
            rule("a", webtoon, categoriesAnyOf = listOf(1L, 2L)),
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(3L)),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(2L, 3L))?.readingModeFlag,
        )
    }

    @Test
    fun categoriesNoneOf_rejectsIfPresent() {
        val rules = listOf(
            rule("a", webtoon, categoriesNoneOf = listOf(1L)),
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(1L)),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(2L))?.readingModeFlag,
        )
    }

    @Test
    fun combinedCategoryClauses_allMustHold() {
        val rules = listOf(
            rule(
                "a",
                webtoon,
                categoriesAnyOf = listOf(1L),
                categoriesNoneOf = listOf(3L),
            ),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(1L, 2L))?.readingModeFlag,
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = setOf(1L, 3L)),
        )
    }

    @Test
    fun disabledRule_skipped() {
        val rules = listOf(
            rule("a", webtoon, enabled = false),
            rule("b", ltr),
        )
        assertEquals(
            ltr,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = emptySet())?.readingModeFlag,
        )
        assertEquals(
            2,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = emptySet())?.ruleNumber,
        )
    }

    @Test
    fun invalidReadingModeFlag_skipped() {
        val rules = listOf(
            rule("bad", 0),
            rule("good", webtoon),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = emptySet())?.readingModeFlag,
        )
        assertEquals(
            2,
            ReadingModeAutoRulesEvaluator.evaluate(rules, sourceId = 1L, genreTags = emptySet(), categoryIds = emptySet())?.ruleNumber,
        )
    }

    @Test
    fun tagsAnyOf_matchesGenreCaseInsensitive() {
        val rules = listOf(
            rule("a", webtoon, tagsAnyOf = listOf("webtoon", "manhwa")),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(
                rules,
                sourceId = 1L,
                genreTags = setOf("manhwa"),
                categoryIds = emptySet(),
            )?.readingModeFlag,
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(
                rules,
                sourceId = 1L,
                genreTags = setOf("shounen"),
                categoryIds = emptySet(),
            ),
        )
    }

    @Test
    fun tagsAllOf_requiresEveryGenre() {
        val rules = listOf(
            rule("a", webtoon, tagsAllOf = listOf("comedy", "webtoon")),
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(
                rules,
                sourceId = 1L,
                genreTags = setOf("webtoon"),
                categoryIds = emptySet(),
            ),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(
                rules,
                sourceId = 1L,
                genreTags = setOf("webtoon", "comedy"),
                categoryIds = emptySet(),
            )?.readingModeFlag,
        )
    }

    @Test
    fun tagsNoneOf_rejectsIfPresent() {
        val rules = listOf(
            rule("a", webtoon, tagsNoneOf = listOf("novel")),
        )
        assertNull(
            ReadingModeAutoRulesEvaluator.evaluate(
                rules,
                sourceId = 1L,
                genreTags = setOf("novel"),
                categoryIds = emptySet(),
            ),
        )
        assertEquals(
            webtoon,
            ReadingModeAutoRulesEvaluator.evaluate(
                rules,
                sourceId = 1L,
                genreTags = setOf("manga"),
                categoryIds = emptySet(),
            )?.readingModeFlag,
        )
    }

    private fun rule(
        id: String,
        mode: Int,
        enabled: Boolean = true,
        sourceIds: List<Long> = emptyList(),
        tagsAllOf: List<String> = emptyList(),
        tagsAnyOf: List<String> = emptyList(),
        tagsNoneOf: List<String> = emptyList(),
        categoriesAllOf: List<Long> = emptyList(),
        categoriesAnyOf: List<Long> = emptyList(),
        categoriesNoneOf: List<Long> = emptyList(),
    ) = ReadingModeAutoRule(
        id = id,
        enabled = enabled,
        readingModeFlag = mode,
        sourceIds = sourceIds,
        tagsAllOf = tagsAllOf,
        tagsAnyOf = tagsAnyOf,
        tagsNoneOf = tagsNoneOf,
        categoriesAllOf = categoriesAllOf,
        categoriesAnyOf = categoriesAnyOf,
        categoriesNoneOf = categoriesNoneOf,
    )
}
