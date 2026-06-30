package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.source.local.isLocal

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<Source>> {
        return combine(
            preferences.pinnedSources.changes(),
            preferences.groupPinnedSources.changes(),
            preferences.enabledLanguages.changes(),
            preferences.disabledSources.changes(),
            preferences.lastUsedSource.changes(),
            repository.getSources(),
        ) { args ->
            val pinnedSourceIds = args[0] as Set<String>
            val groupPinnedSources = args[1] as Set<String>
            val enabledLanguages = args[2] as Set<String>
            val disabledSources = args[3] as Set<String>
            val lastUsedSource = args[4] as Long
            val sources = args[5] as List<Source>

            sources
                .filter { it.lang in enabledLanguages || it.isLocal() }
                .filterNot { it.id.toString() in disabledSources }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val sourceGroups = groupPinnedSources
                        .filter { entry -> entry.endsWith("|${it.id}") }
                        .map { entry -> entry.substringBeforeLast("|") }
                        .toSet()
                    val source = it.copy(pin = flag, pinnedGroups = sourceGroups)
                    val toFlatten = mutableListOf(source)
                    if (source.id == lastUsedSource) {
                        toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
