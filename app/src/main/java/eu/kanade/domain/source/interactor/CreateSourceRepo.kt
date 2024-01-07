package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.plusAssign

class CreateSourceRepo(private val preferences: SourcePreferences) {

    fun await(name: String): Result {
        // Do not allow invalid formats
        if (!name.matches(repoRegex) || name.startsWith(OFFICIAL_REPO_BASE_URL)) {
            return Result.InvalidUrl
        }

        preferences.extensionRepos() += name.removeSuffix("/index.min.json")

        return Result.Success
    }

    sealed interface Result {
        data object InvalidUrl : Result
        data object Success : Result
    }
}

const val OFFICIAL_REPO_BASE_URL = "https://raw.githubusercontent.com/tachiyomiorg/tachiyomi-extensions/repo"
private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()
