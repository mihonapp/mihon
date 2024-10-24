package tachiyomi.domain.libraryUpdateErrorMessage.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository
import kotlin.Exception

class DeleteLibraryUpdateErrorMessages(
    private val libraryUpdateErrorMessageRepository: LibraryUpdateErrorMessageRepository,
) {

    suspend fun await() = withNonCancellableContext {
        try {
            libraryUpdateErrorMessageRepository.deleteAll()
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
