package eu.kanade.tachiyomi.data.backup.restore

data class RestoreOptions(
    val appSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    val library: Boolean = true,
)
