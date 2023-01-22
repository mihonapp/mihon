package eu.kanade.domain.category.interactor

import eu.kanade.domain.library.service.LibraryPreferences
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.plus

class SetSortModeForCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, type: LibrarySort.Type, direction: LibrarySort.Direction) {
        val category = categoryRepository.get(categoryId) ?: return
        val flags = category.flags + type + direction
        if (preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartial(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.librarySortingMode().set(LibrarySort(type, direction))
            categoryRepository.updateAllFlags(flags)
        }
    }

    suspend fun await(category: Category, type: LibrarySort.Type, direction: LibrarySort.Direction) {
        await(category.id, type, direction)
    }
}
