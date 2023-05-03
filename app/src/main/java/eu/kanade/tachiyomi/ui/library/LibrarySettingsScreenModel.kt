package eu.kanade.tachiyomi.ui.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.util.preference.toggle
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.getAndSet
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.category.interactor.SetDisplayModeForCategory
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.TriStateFilter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setDisplayModeForCategory: SetDisplayModeForCategory = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForCategory = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
) : ScreenModel {

    val trackServices
        get() = trackManager.services.filter { it.isLogged }

    fun togglePreference(preference: (LibraryPreferences) -> Preference<Boolean>) {
        preference(libraryPreferences).toggle()
    }

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriStateFilter>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(category: Category, mode: LibraryDisplayMode) {
        coroutineScope.launchIO {
            setDisplayModeForCategory.await(category, mode)
        }
    }

    fun setSort(category: Category, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        coroutineScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
