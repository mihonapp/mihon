package tachiyomi.domain.reader.service

import tachiyomi.domain.reader.model.ReadingModeAutoRule
import tachiyomi.domain.reader.model.ReadingModeAutoRuleMatch

object ReadingModeAutoRulesEvaluator {

    /** Normalized genre strings from the manga’s [tachiyomi.domain.manga.model.Manga.genre] list. */
    fun normalizeMangaGenres(genre: List<String>?): Set<String> =
        genre.orEmpty().map { normalizeTag(it) }.filter { it.isNotEmpty() }.toSet()

    /** Lower 3 bits of viewer flags; must match app `ReadingMode.MASK`. */
    private const val READING_MODE_MASK = 0x7

    fun evaluate(
        rules: List<ReadingModeAutoRule>,
        sourceId: Long,
        genreTags: Set<String>,
        categoryIds: Set<Long>,
    ): ReadingModeAutoRuleMatch? {
        for ((index, rule) in rules.withIndex()) {
            if (!rule.enabled) continue
            if (!isValidConcreteMode(rule.readingModeFlag)) continue
            if (!matchesSources(rule, sourceId)) continue
            if (!matchesTags(rule, genreTags)) continue
            if (!matchesCategories(rule, categoryIds)) continue
            return ReadingModeAutoRuleMatch(
                readingModeFlag = rule.readingModeFlag,
                ruleNumber = index + 1,
            )
        }
        return null
    }

    private fun isValidConcreteMode(flag: Int): Boolean {
        val masked = flag and READING_MODE_MASK
        return masked != 0 && masked == flag
    }

    private fun matchesSources(rule: ReadingModeAutoRule, sourceId: Long): Boolean {
        if (rule.sourceIds.isEmpty()) return true
        return sourceId in rule.sourceIds
    }

    private fun matchesTags(rule: ReadingModeAutoRule, mangaTags: Set<String>): Boolean {
        if (rule.tagsAllOf.isNotEmpty()) {
            if (!rule.tagsAllOf.all { normalizeTag(it) in mangaTags }) return false
        }
        if (rule.tagsAnyOf.isNotEmpty()) {
            if (rule.tagsAnyOf.none { normalizeTag(it) in mangaTags }) return false
        }
        if (rule.tagsNoneOf.isNotEmpty()) {
            if (rule.tagsNoneOf.any { normalizeTag(it) in mangaTags }) return false
        }
        return true
    }

    private fun normalizeTag(s: String): String = s.trim().lowercase()

    private fun matchesCategories(rule: ReadingModeAutoRule, categoryIds: Set<Long>): Boolean {
        if (rule.categoriesAllOf.isNotEmpty()) {
            if (!rule.categoriesAllOf.all { it in categoryIds }) return false
        }
        if (rule.categoriesAnyOf.isNotEmpty()) {
            if (rule.categoriesAnyOf.none { it in categoryIds }) return false
        }
        if (rule.categoriesNoneOf.isNotEmpty()) {
            if (rule.categoriesNoneOf.any { it in categoryIds }) return false
        }
        return true
    }
}
