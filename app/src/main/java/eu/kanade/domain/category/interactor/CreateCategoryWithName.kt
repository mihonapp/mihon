package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.anyWithName
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority

class CreateCategoryWithName(
    private val categoryRepository: CategoryRepository,
    private val preferences: PreferencesHelper,
) {

    private val initialFlags: Long
        get() {
            return preferences.libraryDisplayMode().get().flag or
                preferences.librarySortingMode().get().flag or
                preferences.librarySortingAscending().get().flag
        }

    suspend fun await(name: String): Result = withContext(NonCancellable) {
        val categories = categoryRepository.getAll()
        if (categories.anyWithName(name)) {
            return@withContext Result.NameAlreadyExistsError
        }

        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        val newCategory = Category(
            id = 0,
            name = name,
            order = nextOrder,
            flags = initialFlags,
        )

        try {
            categoryRepository.insert(newCategory)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        object Success : Result()
        object NameAlreadyExistsError : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
