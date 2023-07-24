package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class DeleteCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long) = withNonCancellableContext {
        try {
            categoryRepository.delete(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = categoryRepository.getAll()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        try {
            categoryRepository.updatePartial(updates)
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
