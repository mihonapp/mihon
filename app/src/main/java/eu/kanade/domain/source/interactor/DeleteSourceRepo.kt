package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.minusAssign

class DeleteSourceRepo(private val preferences: SourcePreferences) {

    fun await(repo: String) {
        preferences.extensionRepos() -= repo
    }
}
