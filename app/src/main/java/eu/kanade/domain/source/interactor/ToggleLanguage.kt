package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleLanguage(
    val preferences: PreferencesHelper,
) {

    fun await(language: String) {
        val enabled = language in preferences.enabledLanguages().get()
        if (enabled) {
            preferences.enabledLanguages() -= language
        } else {
            preferences.enabledLanguages() += language
        }
    }
}
