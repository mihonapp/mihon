package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.sortingMode().get()
        categoryRepository.updateFlagsForAll(sort.type + sort.direction)
    }
}
