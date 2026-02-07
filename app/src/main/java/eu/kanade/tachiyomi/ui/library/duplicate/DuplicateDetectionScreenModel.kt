package eu.kanade.tachiyomi.ui.library.duplicate

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.DuplicateMatchMode
import tachiyomi.domain.manga.interactor.FindDuplicateNovels
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DuplicateDetectionScreenModel(
    private val findDuplicateNovels: FindDuplicateNovels = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<DuplicateDetectionScreenModel.State>(State()) {

    private val pinnedSourceIds: Set<Long> by lazy {
        sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() }.toSet()
    }

    override fun onDispose() {
        super.onDispose()
        // Cancel all ongoing coroutines to prevent DB contention
        screenModelScope.coroutineContext.cancelChildren()
    }

    enum class ContentType {
        ALL,
        MANGA,
        NOVEL,
    }

    data class State(
        val isLoading: Boolean = false,
        val hasStartedAnalysis: Boolean = false,
        val matchMode: DuplicateMatchMode = DuplicateMatchMode.EXACT,
        val contentType: ContentType = ContentType.ALL,
        val duplicateGroups: Map<String, List<MangaWithChapterCount>> = emptyMap(),
        val selection: Set<Long> = emptySet(),
        val showDeleteDialog: Boolean = false,
        val showMoveToCategoryDialog: Boolean = false,
        val categories: List<Category> = emptyList(),
        val selectedCategoryFilters: Set<Long> = emptySet(),
        val sortMode: SortMode = SortMode.NAME,
        val mangaCategories: Map<Long, List<Category>> = emptyMap(), // Manga ID -> Categories
        val showFullUrls: Boolean = false, // Toggle to show full URLs under each novel
        val mangaDownloadCounts: Map<Long, Int> = emptyMap(), // Manga ID -> Download count
        val mangaReadCounts: Map<Long, Int> = emptyMap(), // Manga ID -> Read chapter count
        val pinnedSourceIds: Set<Long> = emptySet(), // Pinned source IDs
        val novelSourceIds: Set<Long> = emptySet(), // Source IDs that are novel sources
    ) {
        val totalDuplicates: Int
            get() = duplicateGroups.values.sumOf { it.size }
            
        val filteredDuplicateGroups: Map<String, List<MangaWithChapterCount>>
            get() {
                // First filter by content type (manga/novel/all)
                val contentFiltered = when (contentType) {
                    ContentType.ALL -> duplicateGroups
                    ContentType.MANGA -> duplicateGroups.mapValues { (_, items) ->
                        items.filter { it.manga.source !in novelSourceIds }
                    }.filter { it.value.size > 1 }
                    ContentType.NOVEL -> duplicateGroups.mapValues { (_, items) ->
                        items.filter { it.manga.source in novelSourceIds }
                    }.filter { it.value.size > 1 }
                }
                
                // Then filter by category
                val filtered = if (selectedCategoryFilters.isEmpty()) {
                    contentFiltered
                } else {
                    contentFiltered.mapValues { (_, novels) ->
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
                    SortMode.CHAPTER_COUNT_DESC -> filtered.entries
                        .sortedByDescending { (_, novels) ->
                            novels.sumOf { it.chapterCount }
                        }
                        .associate { it.key to it.value }
                    SortMode.CHAPTER_COUNT_ASC -> filtered.entries
                        .sortedBy { (_, novels) ->
                            novels.sumOf { it.chapterCount }
                        }
                        .associate { it.key to it.value }
                    SortMode.DOWNLOAD_COUNT_DESC -> filtered.entries
                        .sortedByDescending { (_, novels) ->
                            novels.sumOf { mangaDownloadCounts[it.manga.id] ?: 0 }
                        }
                        .associate { it.key to it.value }
                    SortMode.DOWNLOAD_COUNT_ASC -> filtered.entries
                        .sortedBy { (_, novels) ->
                            novels.sumOf { mangaDownloadCounts[it.manga.id] ?: 0 }
                        }
                        .associate { it.key to it.value }
                    SortMode.READ_COUNT_DESC -> filtered.entries
                        .sortedByDescending { (_, novels) ->
                            novels.sumOf { mangaReadCounts[it.manga.id] ?: 0 }
                        }
                        .associate { it.key to it.value }
                    SortMode.READ_COUNT_ASC -> filtered.entries
                        .sortedBy { (_, novels) ->
                            novels.sumOf { mangaReadCounts[it.manga.id] ?: 0 }
                        }
                        .associate { it.key to it.value }
                    SortMode.PINNED_SOURCE -> filtered.entries
                        .sortedByDescending { (_, novels) ->
                            // Groups with more pinned source novels come first
                            novels.count { it.manga.source in pinnedSourceIds }
                        }
                        .associate { it.key to it.value }
                }
            }
        
        // Helper to check if a manga is from a pinned source
        fun isMangaPinned(manga: Manga): Boolean = manga.source in pinnedSourceIds
    }
    
    enum class SortMode {
        NAME,
        LATEST_ADDED,
        CHAPTER_COUNT_DESC,
        CHAPTER_COUNT_ASC,
        DOWNLOAD_COUNT_DESC,
        DOWNLOAD_COUNT_ASC,
        READ_COUNT_DESC,
        READ_COUNT_ASC,
        PINNED_SOURCE,
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
            mutableState.update { it.copy(isLoading = true, hasStartedAnalysis = true) }
            try {
                val groups = findDuplicateNovels.findDuplicatesGrouped(state.value.matchMode)
                
                // Load categories for all manga in duplicate groups
                val allMangaIds = groups.values.flatten().map { it.manga.id }
                val mangaCategoriesMap = allMangaIds.associateWith { mangaId ->
                    getCategories.await(mangaId)
                }
                
                // Build set of novel source IDs for content type filtering
                val allSourceIds = groups.values.flatten().map { it.manga.source }.distinct()
                val novelSourceIds = allSourceIds.filter { sourceId ->
                    sourceManager.getOrStub(sourceId).isNovelSource()
                }.toSet()
                
                // Load download counts for all manga
                val downloadCounts = mutableMapOf<Long, Int>()
                val readCounts = mutableMapOf<Long, Int>()
                
                groups.values.flatten().forEach { mangaWithCount ->
                    val manga = mangaWithCount.manga
                    // Get download count
                    downloadCounts[manga.id] = downloadManager.getDownloadCount(manga)
                    
                    // Get read count from chapters
                    val chapters = getChaptersByMangaId.await(manga.id)
                    readCounts[manga.id] = chapters.count { it.read }
                }
                
                mutableState.update { it.copy(
                    duplicateGroups = groups, 
                    mangaCategories = mangaCategoriesMap,
                    novelSourceIds = novelSourceIds,
                    mangaDownloadCounts = downloadCounts,
                    mangaReadCounts = readCounts,
                    pinnedSourceIds = pinnedSourceIds,
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

    fun setContentType(contentType: ContentType) {
        mutableState.update { it.copy(contentType = contentType, selection = emptySet()) }
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

    fun toggleShowFullUrls() {
        mutableState.update { it.copy(showFullUrls = !it.showFullUrls) }
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
        val allIds = state.value.filteredDuplicateGroups.values.flatten().map { it.manga.id }.toSet()
        val current = state.value.selection
        mutableState.update { it.copy(selection = allIds - current) }
    }

    fun selectAllDuplicates() {
        val allIds = state.value.filteredDuplicateGroups.values.flatten().map { it.manga.id }.toSet()
        mutableState.update { it.copy(selection = allIds) }
    }

    fun selectAllExceptFirst() {
        val ids = state.value.filteredDuplicateGroups.values
            .flatMap { group -> group.drop(1).map { it.manga.id } }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    /**
     * Select the pinned source novel in each group (the one from a pinned source).
     * If no pinned source in a group, selects the first novel.
     */
    fun selectPinnedInGroups() {
        val pinned = pinnedSourceIds
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                // Find the first novel from a pinned source, or fall back to first
                group.firstOrNull { it.manga.source in pinned }?.manga?.id
                    ?: group.firstOrNull()?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    /**
     * Select all novels except those from pinned sources in each group.
     * Useful to keep pinned sources and delete the rest.
     */
    fun selectNonPinnedInGroups() {
        val pinned = pinnedSourceIds
        val ids = state.value.filteredDuplicateGroups.values
            .flatMap { group ->
                group.filter { it.manga.source !in pinned }.map { it.manga.id }
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectLowestChapterCount() {
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                group.minByOrNull { it.chapterCount }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectHighestChapterCount() {
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                group.maxByOrNull { it.chapterCount }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectLowestDownloadCount() {
        val downloadCounts = state.value.mangaDownloadCounts
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                // Filter to only those with downloads > 0
                val withDownloads = group.filter { (downloadCounts[it.manga.id] ?: 0) > 0 }
                withDownloads.minByOrNull { downloadCounts[it.manga.id] ?: 0 }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectHighestDownloadCount() {
        val downloadCounts = state.value.mangaDownloadCounts
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                // Filter to only those with downloads > 0
                val withDownloads = group.filter { (downloadCounts[it.manga.id] ?: 0) > 0 }
                withDownloads.maxByOrNull { downloadCounts[it.manga.id] ?: 0 }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectLowestReadCount() {
        val readCounts = state.value.mangaReadCounts
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                // Filter to only those with reads > 0
                val withReads = group.filter { (readCounts[it.manga.id] ?: 0) > 0 }
                withReads.minByOrNull { readCounts[it.manga.id] ?: 0 }?.manga?.id
            }
            .toSet()
        mutableState.update { it.copy(selection = ids) }
    }

    fun selectHighestReadCount() {
        val readCounts = state.value.mangaReadCounts
        val ids = state.value.filteredDuplicateGroups.values
            .mapNotNull { group ->
                // Filter to only those with reads > 0
                val withReads = group.filter { (readCounts[it.manga.id] ?: 0) > 0 }
                withReads.maxByOrNull { readCounts[it.manga.id] ?: 0 }?.manga?.id
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
            // Process in batches of 100 to avoid overwhelming the database
            selectedIds.chunked(100).forEach { batch ->
                try {
                    // Batch update for better performance
                    val updates = batch.map { mangaId ->
                        tachiyomi.domain.manga.model.MangaUpdate(
                            id = mangaId,
                            favorite = false,
                        )
                    }
                    // Use batch update if available, otherwise fallback to individual updates
                    mangaRepository.updateAll(updates)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Error batch updating manga favorites: ${e.message}" }
                    // Fallback to individual updates on batch failure
                    batch.forEach { mangaId ->
                        try {
                            mangaRepository.update(
                                tachiyomi.domain.manga.model.MangaUpdate(
                                    id = mangaId,
                                    favorite = false,
                                ),
                            )
                        } catch (individualError: Exception) {
                            logcat(LogPriority.ERROR) { "Error updating manga $mangaId: ${individualError.message}" }
                        }
                    }
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
