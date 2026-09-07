package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore
import java.nio.file.Files

data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<LibraryManga> = emptyList(),
    val selectedMangaId: Long? = null,
    val errorMessage: String? = null,
    val categories: List<DesktopCategory> = listOf(SYSTEM_ALL_CATEGORY),
    val selectedCategoryId: Long = SYSTEM_ALL_CATEGORY.id,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.ComfortableGrid,
    val gridSize: Float = 180f,
    val filterState: LibraryFilterState = LibraryFilterState(),
    val sortState: LibrarySortState = LibrarySortState(),
    val selectionState: LibrarySelectionState = LibrarySelectionState(),
    val isFilterDialogOpen: Boolean = false,
    val isBatchCategoryDialogOpen: Boolean = false,
)

data class MangaDetailUiState(
    val manga: MangaDetails? = null,
    val chapters: List<LibraryChapter> = emptyList(),
    val allChapters: List<LibraryChapter> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val readerAvailability: Map<Long, ChapterReaderAvailability> = emptyMap(),
    val chapterFilterState: ChapterFilterState = ChapterFilterState(),
    val chapterSortState: ChapterSortState = ChapterSortState(),
    val isEditInfoDialogOpen: Boolean = false,
    val downloadedChapterIds: Set<Long> = emptySet(),
)

sealed interface ChapterReaderAvailability {
    data object Readable : ChapterReaderAvailability
    data object MissingLocalContent : ChapterReaderAvailability
    data object RemoteOnly : ChapterReaderAvailability
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPresenter(
    private val repository: LibraryRepository,
    scope: CoroutineScope,
    private val readerLibrary: ReaderLibraryPort? = repository as? ReaderLibraryPort,
    private val mutationPort: LibraryMutationPort? = repository as? LibraryMutationPort,
    private val preferences: DesktopPreferenceStore? = null,
    private val downloader: DesktopDownloader? = null,
) : AutoCloseable {
    private val presenterJob = SupervisorJob(scope.coroutineContext[Job])
    private val presenterScope = CoroutineScope(scope.coroutineContext + presenterJob)
    private val query = MutableStateFlow("")
    private val selectedMangaId = MutableStateFlow<Long?>(null)
    private val selectedCategoryId = MutableStateFlow(SYSTEM_ALL_CATEGORY.id)
    private val retryRequest = MutableStateFlow(0L)
    private val detailRetryRequest = MutableStateFlow(0L)

    // Phase 11: Display mode, grid size, filtering, sorting, selection flows
    private val initialPrefs = preferences?.load()

    private val displayModeState = MutableStateFlow(
        initialPrefs?.libraryDisplayMode?.let { runCatching { LibraryDisplayMode.valueOf(it) }.getOrNull() }
            ?: LibraryDisplayMode.ComfortableGrid,
    )

    private val gridSizeState = MutableStateFlow(
        initialPrefs?.libraryGridSize ?: 180f,
    )

    private val filterStateFlow = MutableStateFlow(
        LibraryFilterState(
            unread = parseTriState(initialPrefs?.libraryFilterUnread),
            downloaded = parseTriState(initialPrefs?.libraryFilterDownloaded),
            started = parseTriState(initialPrefs?.libraryFilterStarted),
            completed = parseTriState(initialPrefs?.libraryFilterCompleted),
            bookmarked = parseTriState(initialPrefs?.libraryFilterBookmarked),
        ),
    )

    private val sortStateFlow = MutableStateFlow(
        LibrarySortState(
            mode = initialPrefs?.librarySortMode?.let { runCatching { LibrarySortMode.valueOf(it) }.getOrNull() }
                ?: LibrarySortMode.None,
            ascending = initialPrefs?.librarySortAscending ?: true,
        ),
    )

    private val selectionStateFlow = MutableStateFlow(LibrarySelectionState())
    private val isFilterDialogOpenState = MutableStateFlow(false)
    private val isBatchCategoryDialogOpenState = MutableStateFlow(false)

    private val categoriesState: Flow<List<DesktopCategory>> = repository.observeCategories()
        .map { records ->
            listOf(SYSTEM_ALL_CATEGORY) + records.map {
                DesktopCategory(id = it.id, name = it.name, order = it.sortOrder, flags = it.flags)
            }
        }
        .flowOn(Dispatchers.IO)

    private val repositoryState: Flow<RepositoryState> = combine(
        retryRequest,
        selectedCategoryId,
    ) { _, catId -> catId }
        .flatMapLatest { catId ->
            flow { emitAll(repository.observeLibrary(catId)) }
                .map<List<LibraryManga>, RepositoryState>(RepositoryState::Loaded)
                .onStart { emit(RepositoryState.Loading) }
                .catch { error ->
                    emit(RepositoryState.Failed(error.message ?: "Unable to load library"))
                }
        }
        .flowOn(Dispatchers.IO)
        .onEach { result ->
            if (result is RepositoryState.Loaded) {
                selectedMangaId.update { selected ->
                    selected?.takeIf { id -> result.items.any { it.id == id } }
                }
            }
        }

    private val filterAndSortFlow = combine(filterStateFlow, sortStateFlow) { f, s -> Pair(f, s) }

    val state: StateFlow<LibraryUiState> = combine(
        repositoryState,
        query,
        categoriesState,
        selectedCategoryId,
        filterAndSortFlow,
    ) { repoResult, q, categories, selectedCatId, (filters, sort) ->
        ProcessedLibraryData(repoResult, q, categories, selectedCatId, filters, sort)
    }.combine(
        combine(
            selectedMangaId,
            displayModeState,
            gridSizeState,
            selectionStateFlow,
            isFilterDialogOpenState,
        ) { selId, mode, size, selState, filterOpen ->
            LayoutAndSelectionData(selId, mode, size, selState, filterOpen)
        },
    ) { processed, layout ->
        when (processed.repoResult) {
            RepositoryState.Loading -> LibraryUiState(
                loading = true,
                query = processed.query,
                categories = processed.categories,
                selectedCategoryId = processed.selectedCategoryId,
                displayMode = layout.displayMode,
                gridSize = layout.gridSize,
                filterState = processed.filters,
                sortState = processed.sort,
                selectionState = layout.selectionState,
                isFilterDialogOpen = layout.isFilterDialogOpen,
                isBatchCategoryDialogOpen = isBatchCategoryDialogOpenState.value,
            )
            is RepositoryState.Failed -> LibraryUiState(
                loading = false,
                query = processed.query,
                errorMessage = processed.repoResult.message,
                categories = processed.categories,
                selectedCategoryId = processed.selectedCategoryId,
                displayMode = layout.displayMode,
                gridSize = layout.gridSize,
                filterState = processed.filters,
                sortState = processed.sort,
                selectionState = layout.selectionState,
                isFilterDialogOpen = layout.isFilterDialogOpen,
                isBatchCategoryDialogOpen = isBatchCategoryDialogOpenState.value,
            )
            is RepositoryState.Loaded -> {
                val normalizedQuery = processed.query.trim()
                val searchedItems = if (normalizedQuery.isEmpty()) {
                    processed.repoResult.items
                } else {
                    processed.repoResult.items.filter { manga ->
                        manga.title.contains(normalizedQuery, ignoreCase = true) ||
                            manga.author?.contains(normalizedQuery, ignoreCase = true) == true
                    }
                }

                val filteredItems = searchedItems.filter { matchesFilters(it, processed.filters) }
                val sortedItems = sortManga(filteredItems, processed.sort)

                LibraryUiState(
                    loading = false,
                    query = processed.query,
                    items = sortedItems,
                    selectedMangaId = layout.selectedMangaId?.takeIf { id -> sortedItems.any { it.id == id } },
                    categories = processed.categories,
                    selectedCategoryId = processed.selectedCategoryId,
                    displayMode = layout.displayMode,
                    gridSize = layout.gridSize,
                    filterState = processed.filters,
                    sortState = processed.sort,
                    selectionState = layout.selectionState,
                    isFilterDialogOpen = layout.isFilterDialogOpen,
                    isBatchCategoryDialogOpen = isBatchCategoryDialogOpenState.value,
                )
            }
        }
    }.stateIn(
        scope = presenterScope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(),
    )

    private val selectedRepositoryState: Flow<SelectedRepositoryState> = combine(
        selectedMangaId,
        detailRetryRequest,
    ) { selected, _ -> selected }
        .flatMapLatest { selectedId ->
            if (selectedId == null) {
                flow<SelectedRepositoryState> {
                    emit(SelectedRepositoryState.Loaded(null, null, emptyList(), emptyMap()))
                }
            } else {
                flow<SelectedRepositoryState> {
                    val manga = repository.observeManga(selectedId)
                    val chapters = repository.observeChapters(selectedId)
                    emitAll(
                        combine(manga, chapters) { details, rows ->
                            SelectedRepositoryState.Loaded(
                                selectedId,
                                details,
                                rows,
                                rows.associate { chapter ->
                                    chapter.id to chapter.readerAvailability(readerLibrary)
                                },
                            )
                        },
                    )
                }
                    .onStart { emit(SelectedRepositoryState.Loading) }
                    .catch { error ->
                        emit(SelectedRepositoryState.Failed(error.message ?: "Unable to load manga details"))
                    }
            }
        }
        .flowOn(Dispatchers.IO)
        .onEach { result ->
            if (result is SelectedRepositoryState.Loaded && result.selectedId != null && result.manga == null) {
                selectedMangaId.compareAndSet(result.selectedId, null)
            }
        }

    private val chapterFilterStateFlow = MutableStateFlow(ChapterFilterState())
    private val chapterSortStateFlow = MutableStateFlow(ChapterSortState())
    private val isEditInfoDialogOpenState = MutableStateFlow(false)

    val detailState: StateFlow<MangaDetailUiState> = combine(
        selectedRepositoryState,
        chapterFilterStateFlow,
        chapterSortStateFlow,
        isEditInfoDialogOpenState,
    ) { result, filter, sort, isEditInfoOpen ->
        when (result) {
            SelectedRepositoryState.Loading -> MangaDetailUiState(loading = true)
            is SelectedRepositoryState.Failed -> MangaDetailUiState(errorMessage = result.message)
            is SelectedRepositoryState.Loaded -> {
                val manga = result.manga
                val rawChapters = result.chapters
                val downloadedIds = if (manga != null && downloader != null) {
                    rawChapters.filter { ch ->
                        downloader.diskProvider.isChapterDownloaded(manga.sourceId, manga.title, ch.name)
                    }.map { it.id }.toSet()
                } else {
                    emptySet()
                }

                val filtered = rawChapters.filter { ch ->
                    val unreadMatch = when (filter.unread) {
                        TriStateFilter.Disabled -> true
                        TriStateFilter.Include -> !ch.read
                        TriStateFilter.Exclude -> ch.read
                    }
                    val bookmarkMatch = when (filter.bookmarked) {
                        TriStateFilter.Disabled -> true
                        TriStateFilter.Include -> ch.bookmark
                        TriStateFilter.Exclude -> !ch.bookmark
                    }
                    val downloadedMatch = when (filter.downloaded) {
                        TriStateFilter.Disabled -> true
                        TriStateFilter.Include -> downloadedIds.contains(ch.id)
                        TriStateFilter.Exclude -> !downloadedIds.contains(ch.id)
                    }
                    unreadMatch && bookmarkMatch && downloadedMatch
                }

                val sorted = when (sort.mode) {
                    ChapterSortMode.SourceOrder -> if (sort.ascending) {
                        filtered.sortedBy { it.sourceOrder }
                    } else {
                        filtered.sortedByDescending { it.sourceOrder }
                    }
                    ChapterSortMode.ChapterNumber -> if (sort.ascending) {
                        filtered.sortedBy { it.chapterNumber }
                    } else {
                        filtered.sortedByDescending { it.chapterNumber }
                    }
                    ChapterSortMode.UploadDate -> if (sort.ascending) {
                        filtered.sortedBy { it.dateUpload }
                    } else {
                        filtered.sortedByDescending { it.dateUpload }
                    }
                }

                MangaDetailUiState(
                    manga = manga,
                    chapters = sorted,
                    allChapters = rawChapters,
                    readerAvailability = result.readerAvailability,
                    chapterFilterState = filter,
                    chapterSortState = sort,
                    isEditInfoDialogOpen = isEditInfoOpen,
                    downloadedChapterIds = downloadedIds,
                )
            }
        }
    }
        .stateIn(
            scope = presenterScope,
            started = SharingStarted.Eagerly,
            initialValue = MangaDetailUiState(),
        )

    fun setChapterFilter(filter: ChapterFilterState) {
        chapterFilterStateFlow.value = filter
    }

    fun setChapterSort(sort: ChapterSortState) {
        chapterSortStateFlow.value = sort
    }

    fun setEditInfoDialogOpen(open: Boolean) {
        isEditInfoDialogOpenState.value = open
    }

    fun toggleChapterBookmark(chapterId: Long) {
        val mangaId = selectedMangaId.value ?: return
        val currentChapters = repository.chapterSnapshot(mangaId)
        val chapter = currentChapters.find { it.id == chapterId } ?: return
        val updated = chapter.copy(bookmark = !chapter.bookmark)
        mutationPort?.updateChapter(updated.toChapterRecord())
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun toggleChapterRead(chapterId: Long) {
        val mangaId = selectedMangaId.value ?: return
        val currentChapters = repository.chapterSnapshot(mangaId)
        val chapter = currentChapters.find { it.id == chapterId } ?: return
        val newRead = !chapter.read
        val updated = chapter.copy(
            read = newRead,
            lastPageRead = if (!newRead) 0L else chapter.lastPageRead,
            lastModifiedAt = System.currentTimeMillis(),
        )
        mutationPort?.updateChapter(updated.toChapterRecord())
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun markPreviousChaptersRead(chapterId: Long) {
        val mangaId = selectedMangaId.value ?: return
        val currentChapters = repository.chapterSnapshot(mangaId)
        val target = currentChapters.find { it.id == chapterId } ?: return
        val now = System.currentTimeMillis()
        mutationPort?.transaction {
            currentChapters.forEach { ch ->
                if (ch.sourceOrder > target.sourceOrder || ch.chapterNumber < target.chapterNumber) {
                    if (!ch.read) {
                        updateChapter(ch.copy(read = true, lastModifiedAt = now).toChapterRecord())
                    }
                }
            }
        }
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun downloadChapter(chapterId: Long) {
        val manga = detailState.value.manga ?: return
        val ch = detailState.value.allChapters.find { it.id == chapterId } ?: return
        presenterScope.launch {
            downloader?.enqueue(
                sourceId = manga.sourceId,
                mangaId = manga.id,
                mangaTitle = manga.title,
                chapters = listOf(ch),
            )
        }
    }

    fun deleteChapterDownload(chapterId: Long) {
        val manga = detailState.value.manga ?: return
        val ch = detailState.value.allChapters.find { it.id == chapterId } ?: return
        downloader?.diskProvider?.deleteChapter(manga.sourceId, manga.title, ch.name)
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun updateMangaInfo(
        mangaId: Long,
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) {
        val existing = repository.allMangaSnapshot().find { it.id == mangaId } ?: return
        val genreJson = Json.encodeToString(genres)
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            title = title,
            author = author,
            artist = artist,
            description = description,
            genreJson = genreJson,
            status = status,
            notes = notes,
            lastModifiedAt = now,
        )
        mutationPort?.updateManga(updated)
        isEditInfoDialogOpenState.value = false
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun resetMangaInfo(mangaId: Long) {
        val existing = repository.allMangaSnapshot().find { it.id == mangaId } ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            notes = "",
            lastModifiedAt = now,
        )
        mutationPort?.updateManga(updated)
        isEditInfoDialogOpenState.value = false
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun updateSelectedMangaInfo(
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) {
        val id = selectedMangaId.value ?: return
        updateMangaInfo(id, title, author, artist, description, genres, status, notes)
    }

    fun resetSelectedMangaInfo() {
        val id = selectedMangaId.value ?: return
        resetMangaInfo(id)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectManga(id: Long?) {
        selectedMangaId.value = id?.takeIf { selected -> state.value.items.any { it.id == selected } }
    }

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun retry() {
        retryRequest.update { it + 1 }
    }

    fun retryDetail() {
        detailRetryRequest.update { it + 1 }
    }

    // Phase 11: Display Mode, Grid Size, Filtering & Sorting Actions
    fun setDisplayMode(mode: LibraryDisplayMode) {
        displayModeState.value = mode
        preferences?.update {
            setProperty("library.display_mode", mode.name)
        }
    }

    fun setGridSize(size: Float) {
        gridSizeState.value = size
        preferences?.update {
            setProperty("library.grid_size", size.toString())
        }
    }

    fun setFilterState(filters: LibraryFilterState) {
        filterStateFlow.value = filters
        preferences?.update {
            setProperty("library.filter_unread", filters.unread.name)
            setProperty("library.filter_downloaded", filters.downloaded.name)
            setProperty("library.filter_started", filters.started.name)
            setProperty("library.filter_completed", filters.completed.name)
            setProperty("library.filter_bookmarked", filters.bookmarked.name)
        }
    }

    fun setSortState(sort: LibrarySortState) {
        sortStateFlow.value = sort
        preferences?.update {
            setProperty("library.sort_mode", sort.mode.name)
            setProperty("library.sort_ascending", sort.ascending.toString())
        }
    }

    fun setFilterDialogOpen(open: Boolean) {
        isFilterDialogOpenState.value = open
    }

    fun setBatchCategoryDialogOpen(open: Boolean) {
        isBatchCategoryDialogOpenState.value = open
    }

    // Phase 11: Selection & Batch Operations
    fun toggleSelectionMode(enabled: Boolean) {
        selectionStateFlow.update {
            if (!enabled) LibrarySelectionState() else it.copy(isSelectionMode = true)
        }
    }

    fun toggleMangaSelection(mangaId: Long) {
        selectionStateFlow.update { current ->
            val set = current.selectedMangaIds
            val updated = if (set.contains(mangaId)) set - mangaId else set + mangaId
            current.copy(
                isSelectionMode = updated.isNotEmpty() || current.isSelectionMode,
                selectedMangaIds = updated,
            )
        }
    }

    fun selectAll() {
        val allIds = state.value.items.map { it.id }.toSet()
        selectionStateFlow.update {
            it.copy(isSelectionMode = true, selectedMangaIds = allIds)
        }
    }

    fun clearSelection() {
        selectionStateFlow.value = LibrarySelectionState()
    }

    fun batchSetCategories(categoryIds: List<Long>) {
        val selected = selectionStateFlow.value.selectedMangaIds
        mutationPort?.transaction {
            for (id in selected) {
                setMangaCategories(id, categoryIds)
            }
        }
        clearSelection()
        isBatchCategoryDialogOpenState.value = false
        retry()
    }

    fun batchMarkRead(read: Boolean) {
        val selected = selectionStateFlow.value.selectedMangaIds
        mutationPort?.transaction {
            for (mangaId in selected) {
                val chapters = repository.chapterSnapshot(mangaId)
                for (ch in chapters) {
                    val record = findChapter(mangaId, ch.url)
                    if (record != null) {
                        updateChapter(record.copy(read = read))
                    }
                }
            }
        }
        clearSelection()
        retry()
    }

    fun batchDownload(chaptersCount: Int) {
        val dl = downloader ?: return
        val selected = selectionStateFlow.value.selectedMangaIds
        presenterScope.launch(Dispatchers.IO) {
            val allManga = repository.librarySnapshot(null).associateBy { it.id }
            for (mangaId in selected) {
                val manga = allManga[mangaId] ?: continue
                val chapters = repository.chapterSnapshot(mangaId)
                val unreadChapters = chapters.filter { !it.read }
                val targetChapters = if (chaptersCount <= 0) {
                    unreadChapters
                } else {
                    unreadChapters.take(chaptersCount)
                }
                if (targetChapters.isNotEmpty()) {
                    dl.enqueue(manga, targetChapters)
                }
            }
        }
        clearSelection()
    }

    fun batchRemoveFromLibrary() {
        val selected = selectionStateFlow.value.selectedMangaIds
        mutationPort?.transaction {
            for (mangaId in selected) {
                val manga = repository.librarySnapshot(null).find { it.id == mangaId }
                if (manga != null) {
                    val record = findManga(manga.sourceId, manga.url)
                    if (record != null) {
                        updateManga(record.copy(favorite = false))
                    }
                }
            }
        }
        clearSelection()
        retry()
    }

    override fun close() {
        presenterScope.cancel()
    }
}

private fun parseTriState(value: String?): TriStateFilter {
    return value?.let { runCatching { TriStateFilter.valueOf(it) }.getOrNull() } ?: TriStateFilter.Disabled
}

private fun matchesFilters(manga: LibraryManga, filters: LibraryFilterState): Boolean {
    // 1. Unread
    when (filters.unread) {
        TriStateFilter.Include -> if (manga.unreadCount <= 0) return false
        TriStateFilter.Exclude -> if (manga.unreadCount > 0) return false
        TriStateFilter.Disabled -> Unit
    }
    // 2. Started (some read, some unread)
    when (filters.started) {
        TriStateFilter.Include -> if (!(manga.chapterCount > manga.unreadCount && manga.unreadCount > 0)) return false
        TriStateFilter.Exclude -> if (manga.chapterCount > manga.unreadCount && manga.unreadCount > 0) return false
        TriStateFilter.Disabled -> Unit
    }
    // 3. Completed (has chapters and 0 unread)
    when (filters.completed) {
        TriStateFilter.Include -> if (!(manga.unreadCount == 0L && manga.chapterCount > 0)) return false
        TriStateFilter.Exclude -> if (manga.unreadCount == 0L && manga.chapterCount > 0) return false
        TriStateFilter.Disabled -> Unit
    }
    // 4. Downloaded
    when (filters.downloaded) {
        TriStateFilter.Include -> Unit // Default pass
        TriStateFilter.Exclude -> Unit
        TriStateFilter.Disabled -> Unit
    }
    // 5. Bookmarked
    when (filters.bookmarked) {
        TriStateFilter.Include -> Unit
        TriStateFilter.Exclude -> Unit
        TriStateFilter.Disabled -> Unit
    }
    return true
}

private fun sortManga(items: List<LibraryManga>, sort: LibrarySortState): List<LibraryManga> {
    if (sort.mode == LibrarySortMode.None) return items
    val comparator: Comparator<LibraryManga> = when (sort.mode) {
        LibrarySortMode.None -> return items
        LibrarySortMode.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySortMode.UnreadCount -> compareBy { it.unreadCount }
        LibrarySortMode.TotalChapters -> compareBy { it.chapterCount }
        LibrarySortMode.DateAdded -> compareBy { it.id }
        LibrarySortMode.LastRead -> compareBy { it.id }
        LibrarySortMode.LastUpdate -> compareBy { it.id }
    }

    return if (sort.ascending) {
        items.sortedWith(comparator)
    } else {
        items.sortedWith(comparator.reversed())
    }
}

private data class ProcessedLibraryData(
    val repoResult: RepositoryState,
    val query: String,
    val categories: List<DesktopCategory>,
    val selectedCategoryId: Long,
    val filters: LibraryFilterState,
    val sort: LibrarySortState,
)

private data class LayoutAndSelectionData(
    val selectedMangaId: Long?,
    val displayMode: LibraryDisplayMode,
    val gridSize: Float,
    val selectionState: LibrarySelectionState,
    val isFilterDialogOpen: Boolean,
)

private sealed interface RepositoryState {
    data object Loading : RepositoryState
    data class Loaded(val items: List<LibraryManga>) : RepositoryState
    data class Failed(val message: String) : RepositoryState
}

private sealed interface SelectedRepositoryState {
    data object Loading : SelectedRepositoryState
    data class Loaded(
        val selectedId: Long?,
        val manga: MangaDetails?,
        val chapters: List<LibraryChapter>,
        val readerAvailability: Map<Long, ChapterReaderAvailability>,
    ) : SelectedRepositoryState
    data class Failed(val message: String) : SelectedRepositoryState
}

private fun LibraryChapter.readerAvailability(readerLibrary: ReaderLibraryPort?): ChapterReaderAvailability {
    val asset = readerLibrary?.chapterAsset(id) ?: return ChapterReaderAvailability.RemoteOnly
    return if (Files.isRegularFile(asset.storageRoot.resolve(asset.relativePath))) {
        ChapterReaderAvailability.Readable
    } else {
        ChapterReaderAvailability.MissingLocalContent
    }
}

private fun LibraryChapter.toChapterRecord(): ChapterRecord = ChapterRecord(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    lastModifiedAt = lastModifiedAt,
    version = version,
    memoJson = memoJson,
)
