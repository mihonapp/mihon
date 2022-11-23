package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import java.util.Collections

class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {

    private val mutex = Mutex()

    suspend fun moveUp(category: Category): Result =
        await(category, MoveTo.UP)

    suspend fun moveDown(category: Category): Result =
        await(category, MoveTo.DOWN)

    private suspend fun await(category: Category, moveTo: MoveTo) = withNonCancellableContext {
        mutex.withLock {
            val categories = categoryRepository.getAll()
                .filterNot(Category::isSystemCategory)
                .toMutableList()

            val newPosition = when (moveTo) {
                MoveTo.UP -> category.order - 1
                MoveTo.DOWN -> category.order + 1
            }.toInt()

            val currentIndex = categories.indexOfFirst { it.id == category.id }
            if (currentIndex == newPosition) {
                return@withNonCancellableContext Result.Unchanged
            }

            Collections.swap(categories, currentIndex, newPosition)

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
    }

    sealed class Result {
        object Success : Result()
        object Unchanged : Result()
        data class InternalError(val error: Throwable) : Result()
    }

    private enum class MoveTo {
        UP,
        DOWN,
    }
}
