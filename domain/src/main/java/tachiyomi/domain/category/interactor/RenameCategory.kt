package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

@Inject
class RenameCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(category: Category, name: String, parentId: Long? = category.parentId): Result {
        return try {
            if (category.parentId == parentId) {
                categoryRepository.updateName(category.id, name)
            } else {
                categoryRepository.updateNameAndParent(category.id, name, parentId)
            }
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
