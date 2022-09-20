package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.domain.library.model.plus
import eu.kanade.domain.library.service.LibraryPreferences

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
