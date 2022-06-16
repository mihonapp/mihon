package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.repository.CategoryRepository

class InsertCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(name: String, order: Long): Result {
        return try {
            categoryRepository.insert(name, order)
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
