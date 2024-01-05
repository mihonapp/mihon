package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences

class DeleteSourceRepos(private val preferences: SourcePreferences) {

    fun await(repos: List<String>) {
        preferences.extensionRepos().set(
            preferences.extensionRepos().get().filterNot { it in repos }.toSet(),
        )
    }
}
