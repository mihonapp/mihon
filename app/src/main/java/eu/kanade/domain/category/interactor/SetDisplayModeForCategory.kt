package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting

class SetDisplayModeForCategory(
    private val preferences: PreferencesHelper,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(category: Category, displayModeSetting: DisplayModeSetting) {
        val flags = category.flags and DisplayModeSetting.MASK.inv() or (displayModeSetting.flag and DisplayModeSetting.MASK)
        if (preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartial(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.libraryDisplayMode().set(displayModeSetting)
            categoryRepository.updateAllFlags(flags)
        }
    }
}
