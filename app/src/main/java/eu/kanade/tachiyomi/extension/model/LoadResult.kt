package eu.kanade.tachiyomi.extension.model

sealed class LoadResult {
    data class Success(val extension: Extension.Installed) : LoadResult()
    data class Untrusted(val extension: Extension.Untrusted) : LoadResult()
    data object Error : LoadResult()
}
