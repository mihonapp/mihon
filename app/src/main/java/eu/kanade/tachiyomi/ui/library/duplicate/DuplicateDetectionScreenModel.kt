package eu.kanade.tachiyomi.ui.library.duplicate

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.DuplicateMatchMode
import tachiyomi.domain.manga.interactor.FindDuplicateNovels
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DuplicateDetectionScreenModel(
    private val findDuplicateNovels: FindDuplicateNovels = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : StateScreenModel<DuplicateDetectionScreenModel.State>(State()) {

    override fun onDispose() {
        super.onDispose()
        // Cancel all ongoing coroutines to prevent DB contention
        screenModelScope.coroutineContext.cancelChildren()
    }

    data class State(
        val isLoading: Boolean = true,
        val matchMode: DuplicateMatchMode = DuplicateMatchMode.EXACT,
        val duplicateGroups: Map<String, List<MangaWithChapterCount>> = emptyMap(),
        val selection: Set<Long> = emptySet(),
        val showDeleteDialog: Boolean = false,
        val showMoveToCategoryDialog: Boolean = false,
        val categories: List<Category> = emptyList(),
        val selectedCategoryFilters: Set<Long> = emptySet(),
        val sortMode: SortMode = SortMode.NAME,
        val mangaCategories: Map<Long, List<Category>> = emptyMap(), // Manga ID -> Categories
    ) {
        val totalDuplicates: Int
            get() = duplicateGroups.values.sumOf { it.size }
            
        val filteredDuplicateGroups: Map<String, List<MangaWithChapterCount>>
            get() {
                val filtered = if (selectedCategoryFilters.isEmpty()) {
                    duplicateGroups
                } else {
                    duplicateGroups.mapValues { (_, novels) ->
                        novels.filter { novel ->
                            val novelCategories = mangaCategories[novel.manga.id] ?: emptyList()
                            novelCategories.any { it.id in selectedCategoryFilters }
                        }
                    }.filter { it.value.size > 1 } // Keep only groups with 2+ items
                }
                
                return when (sortMode) {
                    SortMode.NAME -> filtered.toSortedMap()
                    SortMode.LATEST_ADDED -> filtered.entries
                        .sortedByDescending { (_, novels) ->
                            novels.maxOfOrNull { it.manga.dateAdded } ?: 0L
                        }
                        .associate { it.key to it.value }
                }
            }
    }
    
    enum class SortMode {
        NAME,
        LATEST_ADDED,
    }

    init {
        loadCategories()
    }

    private fun loadCategories() {
        screenModelScope.launch(Dispatchers.IO) {
            val categories = getCategories.await()
            mutableState.update { it.copy(categories = categories) }
        }
    }

    fun loadDuplicates() {
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(isLoading = true) }
            try {
                val groups = findDuplicateNovels.findDuplicatesGrouped(state.value.matchMode)
                
                // Load categories for all manga in duplicate groups
                val allMangaIds = groups.values.flatten().map { it.manga.id }
                val mangaCategoriesMap = allMangaIds.associateWith { mangaId ->
                    getCategories.await(mangaId)
                }
                
                mutableState.update { it.copy(
                    duplicateGroups = groups, 
                    mangaCategories = mangaCategoriesMap,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                mutableState.update { it.copy(duplicateGroups = emptyMap(), isLoading = false) }
            }
        }
    }

    fun setMatchMode(mode: DuplicateMatchMode) {
        if (mode != state.value.matchMode) {
            mutableState.update { it.copy(matchMode = mode, selection = emptySet()) }
            loadDuplicates()
        }
    }

    fun toggleCategoryFilter(categoryId: Long) {
        mutableState.update { state ->
            val newFilters = if (categoryId in state.selectedCategoryFilters) {
                state.selectedCategoryFilters - categoryId
            } else {
                state.selectedCategoryFilters + categoryId
            }
            state.copy(selectedCategoryFilters = newFilters)
        }
    }

    fun clearCategoryFilters() {
        mutableState.update { it.copy(selectedCategoryFilters = emptySet()) }
    }

    fun setSortMode(mode: SortMode) {
        mutableState.update { it.copy(sortMode = mode) }
    }

    fun toggleSelection(mangaId: Long) {
        mutableState.update { state ->
            val newSelection = if (mangaId in state.selection) {
                state.selection - mangaId
            } else {
                state.selection + mangaId
            }
            state.copy(selection = newSelection)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptySet()) }
    }

    fun invertSelection() {
        val allIds = state.value.duplicateGroups.values.flatten().map { it.manga.id }.toSet()
        val current = state.value.selection
        mutableState.update { it.copy(selection = allIds - current) }
    }

    fun selectAllDuplicates() {
        val allIds = state.value.duplicateGroups.values.flatten().map { it.manga.id }.toSet()
        mutableState.update { it.copy(selection = allIds) }
    }

    fun selectAllExceptFirst() {
        val ids = state.value.duplicateGroups.values
            .flatMap { group -> group.drop(1).map { it.manga.id } }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectLowestChapterCount() {
        val ids = state.value.duplicateGroups.values
            .mapNotNull { group ->
                group.minByOrNull { it.chapterCount }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectHighestChapterCount() {
        val ids = state.value.duplicateGroups.values
            .mapNotNull { group ->
                group.maxByOrNull { it.chapterCount }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectGroup(groupTitle: String) {
        val group = state.value.duplicateGroups[groupTitle] ?: return
        val groupIds = group.map { it.manga.id }.toSet()
        mutableState.update { state ->
            state.copy(selection = state.selection + groupIds)
        }
    }

    fun getSelectedUrls(): List<String> {
        val selectedIds = state.value.selection
        return state.value.duplicateGroups.values
            .flatten()
            .filter { it.manga.id in selectedIds }
            .map { mangaWithCount ->
                val url = mangaWithCount.manga.url
                // If URL doesn't start with http, it's a relative URL from a JS plugin
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    // Try to get the source's base URL
                    // For now, just return the URL as is since we don't have source info here
                    url
                } else {
                    url
                }
            }
    }

    fun openDeleteDialog() {
        mutableState.update { it.copy(showDeleteDialog = true) }
    }

    fun closeDeleteDialog() {
        mutableState.update { it.copy(showDeleteDialog = false) }
    }

    fun openMoveToCategoryDialog() {
        mutableState.update { it.copy(showMoveToCategoryDialog = true) }
    }

    fun closeMoveToCategoryDialog() {
        mutableState.update { it.copy(showMoveToCategoryDialog = false) }
    }

    suspend fun deleteSelected(deleteManga: Boolean, deleteChapters: Boolean) {
        val selectedIds = state.value.selection.toList()
        screenModelScope.launch(Dispatchers.IO) {
            selectedIds.forEach { mangaId ->
                try {
                    // Remove from library (set favorite = false)
                    mangaRepository.update(
                        tachiyomi.domain.manga.model.MangaUpdate(
                            id = mangaId,
                            favorite = false,
                        ),
                    )
                } catch (e: Exception) {
                    // Ignore errors for individual items
                }
            }
            // Clear selection and reload to refresh the list
            mutableState.update { it.copy(selection = emptySet()) }
            loadDuplicates()
        }.join() // Wait for completion before returning
    }

    suspend fun moveSelectedToCategories(categoryIds: List<Long>) {
        val selectedIds = state.value.selection.toList()
        screenModelScope.launch(Dispatchers.IO) {
            mangaRepository.setMangasCategories(selectedIds, categoryIds)
            mutableState.update { it.copy(selection = emptySet()) }
        }
    }
}
