package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleSource(
    private val preferences: PreferencesHelper
) {

    fun await(source: Source) {
        val isEnabled = source.id.toString() !in preferences.disabledSources().get()
        if (isEnabled) {
            preferences.disabledSources() += source.id.toString()
        } else {
            preferences.disabledSources() -= source.id.toString()
        }
    }
}
