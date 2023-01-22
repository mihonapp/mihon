package eu.kanade.domain.category.interactor

import eu.kanade.domain.library.service.LibraryPreferences
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.plus

class ResetCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await() {
        val display = preferences.libraryDisplayMode().get()
        val sort = preferences.librarySortingMode().get()
        categoryRepository.updateAllFlags(display + sort.type + sort.direction)
    }
}
