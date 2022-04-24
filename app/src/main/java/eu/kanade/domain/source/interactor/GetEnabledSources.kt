package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Pins
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: PreferencesHelper
) {

    fun subscribe(): Flow<List<Source>> {
        return preferences.pinnedSources().asFlow()
            .combine(preferences.enabledLanguages().asFlow()) { pinList, enabledLanguages ->
                Config(pinSet = pinList, enabledSources = enabledLanguages)
            }
            .combine(preferences.disabledSources().asFlow()) { config, disabledSources ->
                config.copy(disabledSources = disabledSources)
            }
            .combine(preferences.lastUsedSource().asFlow()) { config, lastUsedSource ->
                config.copy(lastUsedSource = lastUsedSource)
            }
            .combine(repository.getSources()) { (pinList, enabledLanguages, disabledSources, lastUsedSource), sources ->
                val pinsOnTop = preferences.pinsOnTop().get()
                sources
                    .filter { it.lang in enabledLanguages || it.id == LocalSource.ID }
                    .filterNot { it.id.toString() in disabledSources }
                    .flatMap {
                        val flag = if ("${it.id}" in pinList) Pins.pinned else Pins.unpinned
                        val source = it.copy(pin = flag)
                        val toFlatten = mutableListOf(source)
                        if (source.id == lastUsedSource) {
                            toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                        }
                        if (pinsOnTop.not() && Pin.Pinned in source.pin) {
                            toFlatten[0] = toFlatten[0].copy(pin = source.pin + Pin.Forced)
                            toFlatten.add(source.copy(pin = source.pin - Pin.Actual))
                        }
                        toFlatten
                    }
            }
            .distinctUntilChanged()
    }
}

private data class Config(
    val pinSet: Set<String> = setOf(),
    val enabledSources: Set<String> = setOf(),
    val disabledSources: Set<String> = setOf(),
    val lastUsedSource: Long? = null
)
