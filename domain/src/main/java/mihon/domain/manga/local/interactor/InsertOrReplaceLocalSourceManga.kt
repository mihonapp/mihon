package mihon.domain.manga.local.interactor

import eu.kanade.tachiyomi.source.model.SManga
import logcat.LogPriority
import mihon.domain.manga.local.repository.LocalMangaRepository
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat

class InsertOrReplaceLocalSourceManga(
    private val localSourceMangaRepository: LocalMangaRepository,
) {

    suspend fun await(manga: SManga): Result = withNonCancellableContext {
        try {
            localSourceMangaRepository.insertOrReplaceSManga(manga)
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
