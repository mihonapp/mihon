package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
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
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<List<Source>> {
        val sourcesWithContentWarnings = combine(
            repository.getSources(),
            extensionManager.installedExtensionsFlow,
        ) { sources, installedExtensions ->
            val contentWarningBySourceId = installedExtensions
                .flatMap { extension -> extension.sources.map { it.id to extension.contentWarning } }
                .toMap()
            sources to contentWarningBySourceId
        }

        return combine(
            preferences.pinnedSources.changes(),
            preferences.enabledLanguages.changes(),
            preferences.disabledSources.changes(),
            preferences.lastUsedSource.changes(),
            sourcesWithContentWarnings,
        ) { pinnedSourceIds, enabledLanguages, disabledSources, lastUsedSource, (sources, contentWarningBySourceId) ->
            val contentWarningLevel = preferences.contentWarningLevel.get()

            sources
                .filter { it.lang in enabledLanguages || it.isLocal() }
                .filterNot { it.id.toString() in disabledSources }
                .filter { source ->
                    val contentWarning = contentWarningBySourceId[source.id] ?: return@filter true
                    contentWarningLevel.allowsInstalled(contentWarning)
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val source = it.copy(pin = flag)
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
