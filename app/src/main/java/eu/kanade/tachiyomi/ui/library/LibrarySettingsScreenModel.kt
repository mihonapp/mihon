package eu.kanade.tachiyomi.ui.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class LibrarySettingsScreenModel(
    val type: LibraryScreenModel.LibraryType = LibraryScreenModel.LibraryType.Manga,
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setDisplayMode: SetDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    val extensionsFlow = getLibraryManga.subscribe()
        .map { libraryManga ->
            libraryManga.map { it.manga.source }.distinct()
        }
        .distinctUntilChanged()
        .map { sourceIds ->
            sourceIds.mapNotNull { sourceId ->
                val source = sourceManager.getOrStub(sourceId)
                // Filter extensions based on library type
                val isNovel = source.isNovelSource()
                val shouldInclude = when (type) {
                    LibraryScreenModel.LibraryType.All -> true
                    LibraryScreenModel.LibraryType.Manga -> !isNovel
                    LibraryScreenModel.LibraryType.Novel -> isNovel
                }
                if (shouldInclude) sourceId to source.name else null
            }
                .sortedBy { it.second }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList<Pair<Long, String>>(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }

    /**
     * Toggle extension filter.
     * When checked = true, the extension is included (remove from excluded set)
     * When checked = false, the extension is excluded (add to excluded set)
     */
    fun toggleExtensionFilter(sourceId: String, checked: Boolean) {
        val current = libraryPreferences.excludedExtensions().get()
        libraryPreferences.excludedExtensions().set(
            if (checked) {
                // Checked = include = remove from excluded
                current - sourceId
            } else {
                // Unchecked = exclude = add to excluded
                current + sourceId
            },
        )
    }

    /**
     * Check all extensions (include all - clear the excluded set for available extensions)
     */
    fun checkAllExtensions() {
        val availableSourceIds = extensionsFlow.value.map { it.first.toString() }.toSet()
        val current = libraryPreferences.excludedExtensions().get()
        // Remove all available extensions from excluded set
        libraryPreferences.excludedExtensions().set(current - availableSourceIds)
    }

    /**
     * Uncheck all extensions (exclude all - add all available extensions to excluded set)
     */
    fun uncheckAllExtensions() {
        val availableSourceIds = extensionsFlow.value.map { it.first.toString() }.toSet()
        val current = libraryPreferences.excludedExtensions().get()
        // Add all available extensions to excluded set
        libraryPreferences.excludedExtensions().set(current + availableSourceIds)
    }
}
