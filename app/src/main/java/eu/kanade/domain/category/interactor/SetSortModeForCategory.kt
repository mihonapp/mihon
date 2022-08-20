package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting

class SetSortModeForCategory(
    private val preferences: PreferencesHelper,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(category: Category, sortModeSetting: SortModeSetting, sortDirectionSetting: SortDirectionSetting) {
        var flags = category.flags and SortModeSetting.MASK.inv() or (sortModeSetting.flag and SortModeSetting.MASK)
        flags = flags and SortDirectionSetting.MASK.inv() or (sortDirectionSetting.flag and SortDirectionSetting.MASK)
        if (preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartial(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.librarySortingMode().set(sortModeSetting)
            preferences.librarySortingAscending().set(sortDirectionSetting)
            categoryRepository.updateAllFlags(flags)
        }
    }
}
