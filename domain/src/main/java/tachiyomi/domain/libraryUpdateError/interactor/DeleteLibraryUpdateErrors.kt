package tachiyomi.domain.libraryUpdateError.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

class DeleteLibraryUpdateErrors(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository,
) {

    suspend fun await() = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.deleteAll()
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    suspend fun await(errorId: Long) = withNonCancellableContext {
        try {
            libraryUpdateErrorRepository.delete(errorId)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    sealed class Result {
        object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
