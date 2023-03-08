package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class SetDisplayModeForCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, display: LibraryDisplayMode) {
        val category = categoryRepository.get(categoryId) ?: return
        val flags = category.flags + display
        if (preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartial(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.libraryDisplayMode().set(display)
            categoryRepository.updateAllFlags(flags)
        }
    }

    suspend fun await(category: Category, display: LibraryDisplayMode) {
        await(category.id, display)
    }
}
