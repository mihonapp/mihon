package mihon.desktop.library.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import mihon.desktop.library.model.PreferenceSkipReason

data class SupportedPreferencePolicy(
    val appKeys: Set<String> = DEFAULT_APP_KEYS,
    val sourceKeys: Map<String, Set<String>> = emptyMap(),
) {
    fun classifyApp(key: String, value: AndroidPreferenceValue): PreferenceDecision {
        prefixDecision(key)?.let { return it }
        if (key !in appKeys) return PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
        val expectedType = APP_KEY_TYPES[key]
            ?: return PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
        val imported = value.toImportDecision()
            ?: return PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
        return if (imported.type == expectedType) {
            imported
        } else {
            PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
        }
    }

    fun classifySource(sourceKey: String, key: String, value: AndroidPreferenceValue): PreferenceDecision {
        prefixDecision(key)?.let { return it }
        if (key !in sourceKeys[sourceKey].orEmpty()) return PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
        return value.toImportDecision() ?: PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
    }

    private fun prefixDecision(key: String): PreferenceDecision.Skip? = when {
        key.startsWith("__PRIVATE_") -> PreferenceDecision.Skip(PreferenceSkipReason.PRIVATE)
        key.startsWith("__APP_STATE_") -> PreferenceDecision.Skip(PreferenceSkipReason.APP_STATE)
        else -> null
    }

    companion object {
        val DEFAULT_APP_KEYS = setOf(
            "pref_display_mode_library",
            "library_sorting_mode",
            "pref_library_columns_portrait_key",
            "pref_library_columns_landscape_key",
            "default_category",
            "library_update_categories",
            "library_update_categories_exclude",
        )

        private val APP_KEY_TYPES = mapOf(
            "pref_display_mode_library" to "STRING",
            "library_sorting_mode" to "STRING",
            "pref_library_columns_portrait_key" to "INT",
            "pref_library_columns_landscape_key" to "INT",
            "default_category" to "INT",
            "library_update_categories" to "STRING_SET",
            "library_update_categories_exclude" to "STRING_SET",
        )
    }
}

sealed interface PreferenceDecision {
    data class Import(val type: String, val canonicalJson: String) : PreferenceDecision
    data class Skip(val reason: PreferenceSkipReason) : PreferenceDecision
}

@Suppress("REDUNDANT_ELSE_IN_WHEN")
internal fun AndroidPreferenceValue.toImportDecision(): PreferenceDecision.Import? = when (this) {
    is AndroidIntPreferenceValue -> PreferenceDecision.Import("INT", value.toString())
    is AndroidLongPreferenceValue -> PreferenceDecision.Import("LONG", value.toString())
    is AndroidFloatPreferenceValue -> value.takeIf(Float::isFinite)
        ?.let { PreferenceDecision.Import("FLOAT", JsonPrimitive(it).toString()) }
    is AndroidStringPreferenceValue -> PreferenceDecision.Import("STRING", JsonPrimitive(value).toString())
    is AndroidBooleanPreferenceValue -> PreferenceDecision.Import("BOOLEAN", value.toString())
    is AndroidStringSetPreferenceValue -> PreferenceDecision.Import(
        "STRING_SET",
        JsonArray(value.sortedWith(UNICODE_CODE_POINT_COMPARATOR).map(::JsonPrimitive)).toString(),
    )
    else -> null
}

internal val UNICODE_CODE_POINT_COMPARATOR = Comparator<String> { left, right ->
    val leftPoints = left.codePoints().iterator()
    val rightPoints = right.codePoints().iterator()
    while (leftPoints.hasNext() && rightPoints.hasNext()) {
        val comparison = leftPoints.nextInt().compareTo(rightPoints.nextInt())
        if (comparison != 0) return@Comparator comparison
    }
    when {
        leftPoints.hasNext() -> 1
        rightPoints.hasNext() -> -1
        else -> 0
    }
}
