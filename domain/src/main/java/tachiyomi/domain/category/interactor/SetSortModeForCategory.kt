package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class SetSortModeForCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long?, type: LibrarySort.Type, direction: LibrarySort.Direction) {
        val category = categoryId?.let { categoryRepository.get(it) }
        val flags = (category?.flags ?: 0) + type + direction
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartial(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.sortingMode().set(LibrarySort(type, direction))
            categoryRepository.updateAllFlags(flags)
        }
    }

    suspend fun await(
        category: Category?,
        type: LibrarySort.Type,
        direction: LibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
