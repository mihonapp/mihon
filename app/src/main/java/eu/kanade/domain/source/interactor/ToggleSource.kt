package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.core.preference.getAndSet
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleSource(
    private val preferences: PreferencesHelper,
) {

    fun await(source: Source, enable: Boolean = source.id.toString() in preferences.disabledSources().get()) {
        await(source.id, enable)
    }

    fun await(sourceId: Long, enable: Boolean = sourceId.toString() in preferences.disabledSources().get()) {
        preferences.disabledSources().getAndSet { disabled ->
            if (enable) disabled.minus("$sourceId") else disabled.plus("$sourceId")
        }
    }
}
