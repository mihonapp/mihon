package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.plusAssign

class DisableSource(
    private val preferences: PreferencesHelper
) {

    fun await(source: Source) {
        preferences.disabledSources() += source.id.toString()
    }
}
