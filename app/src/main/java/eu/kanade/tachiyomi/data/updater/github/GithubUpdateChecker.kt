package eu.kanade.tachiyomi.data.updater.github

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.UpdateChecker
import eu.kanade.tachiyomi.data.updater.UpdateResult
import exh.syDebugVersion

class GithubUpdateChecker(val debug: Boolean = false) : UpdateChecker() {

    private val service: GithubService = GithubService.create()

    override suspend fun checkForUpdate(): UpdateResult {
        val release = if (syDebugVersion != "0") {
            service.getLatestDebugVersion()
        } else {
            service.getLatestVersion()
        }

        val newVersion = release.version
        // Check if latest version is different from current version
        return if ((newVersion != BuildConfig.VERSION_NAME && (syDebugVersion != "0")) || ((syDebugVersion != "0") && newVersion != syDebugVersion)) {
            GithubUpdateResult.NewUpdate(release)
        } else {
            GithubUpdateResult.NoNewUpdate()
        }
    }
}
