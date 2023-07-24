package tachiyomi.domain.category.interactor

import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class UpdateCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(payload: CategoryUpdate): Result = withNonCancellableContext {
        try {
            categoryRepository.updatePartial(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
