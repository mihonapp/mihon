package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.getAndSet

class ToggleLanguage(
    val preferences: SourcePreferences,
) {

    fun await(language: String) {
        val isEnabled = language in preferences.enabledLanguages().get()
        preferences.enabledLanguages().getAndSet { enabled ->
            if (isEnabled) enabled.minus(language) else enabled.plus(language)
        }
    }
}
