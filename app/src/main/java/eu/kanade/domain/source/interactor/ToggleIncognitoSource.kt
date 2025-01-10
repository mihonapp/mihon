package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet

class ToggleIncognitoSource(
    private val preferences: SourcePreferences,
) {
    fun await(sourceIds: List<Long>, enable: Boolean) {
        val transformedSourceIds = sourceIds.map { it.toString() }
        preferences.incognitoSources().getAndSet { incognitoed ->
            if (enable) incognitoed.plus(transformedSourceIds) else incognitoed.minus(transformedSourceIds)
        }
    }
}
