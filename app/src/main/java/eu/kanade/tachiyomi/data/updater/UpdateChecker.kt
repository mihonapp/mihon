package eu.kanade.tachiyomi.data.updater

import eu.kanade.tachiyomi.data.updater.github.GithubUpdateChecker

abstract class UpdateChecker {

    companion object {
        fun getUpdateChecker(): UpdateChecker {
            return GithubUpdateChecker()
        }
    }

    /**
     * Returns observable containing release information
     */
    abstract suspend fun checkForUpdate(): UpdateResult
}
