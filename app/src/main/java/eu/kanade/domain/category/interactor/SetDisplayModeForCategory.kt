package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode
import eu.kanade.tachiyomi.ui.library.setting.plus

class SetDisplayModeForCategory(
    private val preferences: PreferencesHelper,
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
