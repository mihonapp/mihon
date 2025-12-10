package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.FilterPreset
import eu.kanade.domain.source.model.FilterPresetList
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import logcat.logcat

class ManageFilterPresets(
    private val preferences: SourcePreferences,
) {
    fun getPresets(sourceId: Long): FilterPresetList {
        val presets = preferences.filterPresets(sourceId).get()
        // Split the large log message into two shorter lines to satisfy ktlint max line length
        logcat(LogPriority.DEBUG) {
            "FilterPresets: getPresets for sourceId=$sourceId, found ${presets.presets.size} presets"
        }
        logcat(LogPriority.DEBUG) { "FilterPresets: Preset names: ${presets.presets.map { it.name }}" }
        return presets
    }

    fun getPresetsFlow(sourceId: Long): Flow<List<FilterPreset>> {
        return preferences.filterPresets(sourceId).changes()
            .map { it.presets }
    }

    fun savePreset(
        sourceId: Long,
        name: String,
        filters: FilterList,
        setAsDefault: Boolean = false,
    ): Result<Unit> {
        return runCatching {
            logcat(LogPriority.DEBUG) {
                "FilterPresets: Starting savePreset for sourceId=$sourceId, name=$name, setAsDefault=$setAsDefault"
            }

            val filterState = serializeFilters(filters)
            logcat(LogPriority.DEBUG) { "FilterPresets: Serialized filters to: ${filterState.take(200)}..." }

            val currentPresets = preferences.filterPresets(sourceId).get()
            logcat(LogPriority.DEBUG) { "FilterPresets: Current presets count: ${currentPresets.presets.size}" }

            val newPreset = FilterPreset(
                id = System.currentTimeMillis(),
                sourceId = sourceId,
                name = name,
                filterState = filterState,
                isDefault = setAsDefault,
            )
            logcat(LogPriority.DEBUG) { "FilterPresets: Created new preset with id=${newPreset.id}, name=$name" }

            val updatedPresets = if (setAsDefault) {
                // Remove default flag from other presets
                logcat(LogPriority.DEBUG) {
                    "FilterPresets: Setting as default, clearing default flag from other presets"
                }
                currentPresets.presets.map { it.copy(isDefault = false) } + newPreset
            } else {
                currentPresets.presets + newPreset
            }

            logcat(LogPriority.DEBUG) { "FilterPresets: Updated presets count: ${updatedPresets.size}" }

            preferences.filterPresets(sourceId).set(
                FilterPresetList(updatedPresets),
            )
            logcat(LogPriority.INFO) { "FilterPresets: Successfully saved preset '$name' for sourceId=$sourceId" }
        }.onFailure { error ->
            logcat(LogPriority.ERROR) { "FilterPresets: Failed to save preset: ${error.message}" }
        }
    }

    fun loadPresetState(sourceId: Long, presetId: Long): String? {
        logcat(LogPriority.DEBUG) { "FilterPresets: loadPresetState sourceId=$sourceId, presetId=$presetId" }
        val preset = getPresets(sourceId).presets.find { it.id == presetId }
        if (preset != null) {
            logcat(LogPriority.DEBUG) {
                "FilterPresets: Found preset '${preset.name}' with state length=${preset.filterState.length}"
            }
        } else {
            logcat(LogPriority.WARN) { "FilterPresets: Preset $presetId not found for sourceId=$sourceId" }
        }
        return preset?.filterState
    }

    fun deletePreset(sourceId: Long, presetId: Long) {
        logcat(LogPriority.DEBUG) { "FilterPresets: deletePreset sourceId=$sourceId, presetId=$presetId" }
        val currentPresets = preferences.filterPresets(sourceId).get()
        val presetToDelete = currentPresets.presets.find { it.id == presetId }
        logcat(LogPriority.DEBUG) { "FilterPresets: Found preset to delete: ${presetToDelete?.name}" }
        val updatedPresets = currentPresets.presets.filter { it.id != presetId }
        preferences.filterPresets(sourceId).set(
            FilterPresetList(updatedPresets),
        )
        logcat(LogPriority.INFO) { "FilterPresets: Deleted preset $presetId, remaining=${updatedPresets.size}" }
    }

    fun setDefaultPreset(sourceId: Long, presetId: Long?) {
        logcat(LogPriority.DEBUG) { "FilterPresets: setDefaultPreset sourceId=$sourceId, presetId=$presetId" }
        val currentPresets = preferences.filterPresets(sourceId).get()
        val updatedPresets = currentPresets.presets.map { preset ->
            preset.copy(isDefault = preset.id == presetId)
        }
        preferences.filterPresets(sourceId).set(
            FilterPresetList(updatedPresets),
        )
        val defaultName = updatedPresets.find { it.isDefault }?.name
        logcat(LogPriority.INFO) { "FilterPresets: Set default preset to: $defaultName" }
    }

    fun getDefaultPresetState(sourceId: Long): String? {
        val preset = getPresets(sourceId).presets.find { it.isDefault }
        logcat(LogPriority.DEBUG) {
            "FilterPresets: getDefaultPresetState sourceId=$sourceId, found default=${preset?.name}"
        }
        return preset?.filterState
    }

    fun getAutoApplyEnabled(): Boolean {
        val enabled = preferences.autoApplyFilterPresets().get()
        logcat(LogPriority.DEBUG) { "FilterPresets: getAutoApplyEnabled=$enabled" }
        return enabled
    }

    fun setAutoApplyEnabled(enabled: Boolean) {
        logcat(LogPriority.DEBUG) { "FilterPresets: setAutoApplyEnabled=$enabled" }
        preferences.autoApplyFilterPresets().set(enabled)
        logcat(LogPriority.INFO) { "FilterPresets: Auto-apply presets set to $enabled" }
    }

    companion object {
        fun serializeFilters(filters: FilterList): String {
            val filterStates = filters.mapIndexed { index, filter ->
                val stateJson = when (filter) {
                    is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> JsonPrimitive(filter.state)
                    is eu.kanade.tachiyomi.source.model.Filter.TriState -> JsonPrimitive(filter.state)
                    is eu.kanade.tachiyomi.source.model.Filter.Text -> JsonPrimitive(filter.state)
                    is eu.kanade.tachiyomi.source.model.Filter.Select<*> -> JsonPrimitive(filter.state)
                    is eu.kanade.tachiyomi.source.model.Filter.Sort -> JsonObject(
                        mapOf(
                            "index" to JsonPrimitive(filter.state?.index ?: 0),
                            "ascending" to JsonPrimitive(filter.state?.ascending ?: true),
                        ),
                    )
                    is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> JsonArray(
                        filter.state.mapIndexed { i, groupFilter ->
                            // Break the long conditional into multiple lines to avoid exceeding max line length
                            val name = if (groupFilter is eu.kanade.tachiyomi.source.model.Filter<*>) {
                                groupFilter.name
                            } else {
                                ""
                            }
                            val groupState = when (groupFilter) {
                                is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> JsonPrimitive(groupFilter.state)
                                is eu.kanade.tachiyomi.source.model.Filter.TriState -> JsonPrimitive(groupFilter.state)
                                else -> JsonNull
                            }
                            JsonObject(
                                mapOf(
                                    "index" to JsonPrimitive(i),
                                    "name" to JsonPrimitive(name),
                                    "state" to groupState,
                                ),
                            )
                        },
                    )
                    is eu.kanade.tachiyomi.source.model.Filter.Header -> JsonNull
                    is eu.kanade.tachiyomi.source.model.Filter.Separator -> JsonNull
                    else -> JsonNull
                }
                JsonObject(
                    mapOf(
                        "index" to JsonPrimitive(index),
                        "name" to JsonPrimitive(filter.name),
                        "state" to stateJson,
                    ),
                )
            }
            val result = JsonArray(filterStates).toString()
            logcat(LogPriority.DEBUG) {
                "FilterPresets: serializeFilters ${filters.size} filters -> ${result.length} chars"
            }
            return result
        }

        fun applyPresetState(filters: FilterList, presetState: String) {
            logcat(LogPriority.DEBUG) {
                "FilterPresets: applyPresetState for ${filters.size} filters, state length=${presetState.length}"
            }
            try {
                val states = Json.parseToJsonElement(presetState).jsonArray
                logcat(LogPriority.DEBUG) { "FilterPresets: Deserialized ${states.size} filter states" }

                states.forEach { stateElement ->
                    val stateMap = stateElement.jsonObject
                    val index = stateMap["index"]?.jsonPrimitive?.intOrNull ?: return@forEach
                    if (index >= filters.size) return@forEach

                    val filter = filters[index]
                    val state = stateMap["state"]

                    when (filter) {
                        is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> {
                            state?.jsonPrimitive?.booleanOrNull?.let { filter.state = it }
                        }
                        is eu.kanade.tachiyomi.source.model.Filter.TriState -> {
                            state?.jsonPrimitive?.intOrNull?.let { filter.state = it }
                        }
                        is eu.kanade.tachiyomi.source.model.Filter.Text -> {
                            state?.jsonPrimitive?.content?.let { filter.state = it }
                        }
                        is eu.kanade.tachiyomi.source.model.Filter.Select<*> -> {
                            state?.jsonPrimitive?.intOrNull?.let { filter.state = it }
                        }
                        is eu.kanade.tachiyomi.source.model.Filter.Sort -> {
                            val sortState = state?.jsonObject
                            if (sortState != null) {
                                val sortIndex = sortState["index"]?.jsonPrimitive?.intOrNull ?: 0
                                val ascending = sortState["ascending"]?.jsonPrimitive?.booleanOrNull ?: true
                                filter.state = eu.kanade.tachiyomi.source.model.Filter.Sort.Selection(
                                    index = sortIndex,
                                    ascending = ascending,
                                )
                            }
                        }
                        is eu.kanade.tachiyomi.source.model.Filter.Group<*> -> {
                            val groupStates = state?.jsonArray ?: return@forEach
                            groupStates.forEach { groupElement ->
                                val groupState = groupElement.jsonObject
                                val groupIndex = groupState["index"]?.jsonPrimitive?.intOrNull ?: return@forEach
                                if (groupIndex >= filter.state.size) return@forEach

                                val groupFilter = filter.state[groupIndex]
                                val groupFilterState = groupState["state"]

                                when (groupFilter) {
                                    is eu.kanade.tachiyomi.source.model.Filter.CheckBox -> {
                                        groupFilterState?.jsonPrimitive?.booleanOrNull?.let { groupFilter.state = it }
                                    }
                                    is eu.kanade.tachiyomi.source.model.Filter.TriState -> {
                                        groupFilterState?.jsonPrimitive?.intOrNull?.let { groupFilter.state = it }
                                    }
                                    else -> Unit // Ignore other types
                                }
                            }
                        }
                        is eu.kanade.tachiyomi.source.model.Filter.Header -> Unit // No state to restore
                        is eu.kanade.tachiyomi.source.model.Filter.Separator -> Unit // No state to restore
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "FilterPresets: Failed to applyPresetState: ${e.message}" }
            }
            logcat(LogPriority.INFO) { "FilterPresets: Preset state applied successfully" }
        }
    }
}
