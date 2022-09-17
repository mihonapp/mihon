package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.core.preference.getAndSet
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign

class ToggleLanguage(
    val preferences: PreferencesHelper,
) {

    fun await(language: String) {
        val isEnabled = language in preferences.enabledLanguages().get()
        preferences.enabledLanguages().getAndSet { enabled ->
            if (isEnabled) enabled.minus(language) else enabled.plus(language)
        }
    }
}
