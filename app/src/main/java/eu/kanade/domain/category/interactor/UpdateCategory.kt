package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class UpdateCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(payload: CategoryUpdate): Result = withContext(NonCancellable) {
        try {
            categoryRepository.updatePartial(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed class Result {
        object Success : Result()
        data class Error(val error: Exception) : Result()
    }
}
