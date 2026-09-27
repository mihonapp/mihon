package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

@Inject
class HideCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(category: Category) = withNonCancellableContext {
        try {
            categoryRepository.updateHidden(categoryId = category.id, hidden = !category.hidden)
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
