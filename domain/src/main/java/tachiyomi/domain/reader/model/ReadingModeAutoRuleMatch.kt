package tachiyomi.domain.reader.model

/**
 * Result of evaluating ordered automatic reading mode rules (first match wins).
 *
 * @property ruleNumber 1-based position in the configured rules list (same order as settings).
 */
data class ReadingModeAutoRuleMatch(
    val readingModeFlag: Int,
    val ruleNumber: Int,
)
