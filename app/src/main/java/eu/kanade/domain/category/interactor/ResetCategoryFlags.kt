package eu.kanade.domain.category.interactor

import eu.kanade.domain.category.repository.CategoryRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting

class ResetCategoryFlags(
    private val preferences: PreferencesHelper,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await() {
        val display = preferences.libraryDisplayMode().get()
        val sort = preferences.librarySortingMode().get()
        val sortDirection = preferences.librarySortingAscending().get()

        var flags = 0L
        flags = flags and DisplayModeSetting.MASK.inv() or (display.flag and DisplayModeSetting.MASK)
        flags = flags and SortModeSetting.MASK.inv() or (sort.flag and SortModeSetting.MASK)
        flags = flags and SortDirectionSetting.MASK.inv() or (sortDirection.flag and SortDirectionSetting.MASK)
        categoryRepository.updateAllFlags(flags)
    }
}
