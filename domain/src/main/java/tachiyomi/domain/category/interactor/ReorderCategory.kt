package tachiyomi.domain.category.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import java.util.Collections

class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {

    private val mutex = Mutex()

    suspend fun moveUp(category: Category): Result = await(category, MoveTo.UP)

    suspend fun moveDown(category: Category): Result = await(category, MoveTo.DOWN)

    private suspend fun await(category: Category, moveTo: MoveTo) = withNonCancellableContext {
        mutex.withLock {
            val categories = categoryRepository.getAll()
                .filterNot(Category::isSystemCategory)
                .toMutableList()

            val currentIndex = categories.indexOfFirst { it.id == category.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            val newPosition = when (moveTo) {
                MoveTo.UP -> currentIndex - 1
                MoveTo.DOWN -> currentIndex + 1
            }.toInt()

            try {
                Collections.swap(categories, currentIndex, newPosition)

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

    suspend fun sortAlphabetically() = withNonCancellableContext {
        mutex.withLock {
            val updates = categoryRepository.getAll()
                .sortedBy { category -> category.name }
                .mapIndexed { index, category ->
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
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }

    private enum class MoveTo {
        UP,
        DOWN,
    }
}
