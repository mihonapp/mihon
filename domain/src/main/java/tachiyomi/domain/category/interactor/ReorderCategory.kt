package tachiyomi.domain.category.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {
    private val mutex = Mutex()

    suspend fun await(category: Category, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            val categories = categoryRepository.getAll()
                .filterNot(Category::isSystemCategory)
                .toMutableList()

            val currentIndex = categories.indexOfFirst { it.id == category.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                categories.add(newIndex, categories.removeAt(currentIndex))

                val updates = categories.mapIndexed { index, category ->
                    CategoryUpdate(
                        id = category.id,
                        order = index.toLong(),
                    )
                }

                categoryRepository.updatePartial(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
