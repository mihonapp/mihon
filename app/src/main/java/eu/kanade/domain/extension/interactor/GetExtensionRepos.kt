package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow

class GetExtensionRepos(private val preferences: SourcePreferences) {

    fun subscribe(): Flow<Set<String>> {
        return preferences.extensionRepos().changes()
    }
}
