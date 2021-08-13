package eu.kanade.tachiyomi.data.updater

sealed class AppUpdateResult {
    class NewUpdate(val release: GithubRelease) : AppUpdateResult()
    object NoNewUpdate : AppUpdateResult()
}
