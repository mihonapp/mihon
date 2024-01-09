package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.minusAssign

class DeleteExtensionRepo(private val preferences: SourcePreferences) {

    fun await(repo: String) {
        preferences.extensionRepos() -= repo
    }
}
