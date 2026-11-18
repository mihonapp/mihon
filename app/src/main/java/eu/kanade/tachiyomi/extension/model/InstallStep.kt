package eu.kanade.tachiyomi.extension.model

sealed interface InstallStep {
    data object Idle : InstallStep
    data object Pending : InstallStep
    data object Downloading : InstallStep
    data object Installing : InstallStep
    data object Installed : InstallStep
    data object Error : InstallStep

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Idle
    }
}
