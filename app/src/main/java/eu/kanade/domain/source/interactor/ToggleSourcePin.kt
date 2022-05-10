package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleSourcePin(
    private val preferences: PreferencesHelper,
) {

    fun await(source: Source) {
        val isPinned = source.id.toString() in preferences.pinnedSources().get()
        if (isPinned) {
            preferences.pinnedSources() -= source.id.toString()
        } else {
            preferences.pinnedSources() += source.id.toString()
        }
    }
}
