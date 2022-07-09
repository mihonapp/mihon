package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.model.anyWithName
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority

class RenameCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, name: String) = withContext(NonCancellable) await@{
        val categories = categoryRepository.getAll()
        if (categories.anyWithName(name)) {
            return@await Result.NameAlreadyExistsError
        }

        val update = CategoryUpdate(
            id = categoryId,
            name = name,
        )

        try {
            categoryRepository.updatePartial(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: Category, name: String) = await(category.id, name)

    sealed class Result {
        object Success : Result()
        object NameAlreadyExistsError : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
