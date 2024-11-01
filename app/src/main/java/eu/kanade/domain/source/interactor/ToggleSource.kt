package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.model.Source

class ToggleSource(
    private val preferences: SourcePreferences,
) {

    fun awaitDisable(source: Source, enable: Boolean = isEnabled(source.id)) {
        awaitDisable(source.id, enable)
    }

    fun awaitDisable(sourceId: Long, enable: Boolean = isEnabled(sourceId)) {
        preferences.disabledSources().getAndSet { disabled ->
            if (enable) disabled.minus("$sourceId") else disabled.plus("$sourceId")
        }
    }

    fun awaitDisable(sourceIds: List<Long>, enable: Boolean) {
        val transformedSourceIds = sourceIds.map { it.toString() }
        preferences.disabledSources().getAndSet { disabled ->
            if (enable) disabled.minus(transformedSourceIds) else disabled.plus(transformedSourceIds)
        }
    }

    private fun isEnabled(sourceId: Long): Boolean {
        return sourceId.toString() in preferences.disabledSources().get()
    }
}
