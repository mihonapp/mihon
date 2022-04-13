package eu.kanade.tachiyomi.data.updater

sealed class AppUpdateResult {
    class NewUpdate(val release: GithubRelease) : AppUpdateResult()
    object NewUpdateFdroidInstallation : AppUpdateResult()
    object NoNewUpdate : AppUpdateResult()
}
