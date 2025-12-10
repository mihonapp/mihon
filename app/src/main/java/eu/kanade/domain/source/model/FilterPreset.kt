package eu.kanade.domain.source.model

import kotlinx.serialization.Serializable

@Serializable
data class FilterPreset(
    val id: Long,
    val sourceId: Long,
    val name: String,
    val filterState: String, // JSON-encoded filter state
    val isDefault: Boolean = false,
)

@Serializable
data class FilterPresetList(
    val presets: List<FilterPreset> = emptyList(),
)
