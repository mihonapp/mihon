package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository

class UpdateCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(payload: CategoryUpdate): Result {
        return try {
            categoryRepository.update(payload)
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
