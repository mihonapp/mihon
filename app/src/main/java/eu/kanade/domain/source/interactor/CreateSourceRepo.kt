package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.plusAssign

class CreateSourceRepo(private val preferences: SourcePreferences) {

    fun await(name: String): Result {
        // Do not allow invalid formats
        if (!name.matches(repoRegex)) {
            return Result.InvalidName
        }

        preferences.extensionRepos() += name

        return Result.Success
    }

    sealed class Result {
        data object InvalidName : Result()
        data object Success : Result()
    }

    /**
     * Returns true if a repo with the given name already exists.
     */
    private fun repoExists(name: String): Boolean {
        return preferences.extensionRepos().get().any { it.equals(name, true) }
    }

    companion object {
        val repoRegex = """^[a-zA-Z0-9-_.]*?\/[a-zA-Z0-9-_.]*?$""".toRegex()
    }
}
