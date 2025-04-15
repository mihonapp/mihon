package mihon.domain.manga.local.interactor

import logcat.LogPriority
import mihon.domain.manga.local.repository.LocalMangaRepository
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat

class UpdateThumbnailUrlLocalSource(
    private val localSourceMangaRepository: LocalMangaRepository,
) {

    suspend fun await(url: String, thumbnailUrl: String?): Result = withNonCancellableContext {
        try {
            localSourceMangaRepository.updateThumbnailUrl(url, thumbnailUrl)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
