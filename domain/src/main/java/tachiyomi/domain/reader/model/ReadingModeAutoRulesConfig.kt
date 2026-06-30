package tachiyomi.domain.reader.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadingModeAutoRulesConfig(
    val version: Int = 2,
    val rules: List<ReadingModeAutoRule> = emptyList(),
)

/**
 * A single rule: first match in list order wins.
 *
 * [readingModeFlag] must be a concrete Mihon reading-mode viewer flag (not “default”).
 * [sourceIds] empty = any source. Category and tag lists empty = no constraint for that clause.
 * Tags are matched case-insensitively against the manga’s genre strings from the source.
 */
@Serializable
data class ReadingModeAutoRule(
    val id: String,
    val title: String = "",
    val enabled: Boolean = true,
    /** Non-null for built-in presets; shown in UI and not deletable. */
    val presetId: String? = null,
    val readingModeFlag: Int,
    val sourceIds: List<Long> = emptyList(),
    val tagsAllOf: List<String> = emptyList(),
    val tagsAnyOf: List<String> = emptyList(),
    val tagsNoneOf: List<String> = emptyList(),
    val categoriesAllOf: List<Long> = emptyList(),
    val categoriesAnyOf: List<Long> = emptyList(),
    val categoriesNoneOf: List<Long> = emptyList(),
)

/** Appends missing [ReadingModeAutoRulePresets] entries (disabled) so users can enable them. */
fun ReadingModeAutoRulesConfig.withPresetsMerged(): ReadingModeAutoRulesConfig {
    val existingIds = rules.map { it.id }.toSet()
    val missing = ReadingModeAutoRulePresets.defaults().filter { it.id !in existingIds }
    if (missing.isEmpty()) return this
    return copy(
        version = maxOf(version, 2),
        rules = rules + missing,
    )
}
