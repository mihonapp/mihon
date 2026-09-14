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
import kotlinx.coroutines.flow.emptyFlow
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
import mihon.reader.source.ReaderChapterAsset
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
    val duplicateDialog: DuplicateMangaDialogState? = null,
) {
    val duplicateTarget: DuplicateMangaCandidate? get() = duplicateDialog?.target
    val duplicateCandidates: List<DuplicateMangaCandidate> get() = duplicateDialog?.candidates.orEmpty()
}

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
    val readerChapters: List<LibraryChapter> = emptyList(),
    val chapterSettings: ChapterSettings = ChapterSettings(),
    val availableScanlators: Set<String> = emptySet(),
    val chapterListItems: List<ChapterListItem> = emptyList(),
    val isChapterSettingsDialogOpen: Boolean = false,
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
    private val detailMangaId = MutableStateFlow<Long?>(null)
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
    private val duplicateDialogState = MutableStateFlow<DuplicateMangaDialogState?>(null)
    private val duplicateLock = Any()
    private val knownMangaIds = mutableSetOf<Long>()
    private val dismissedDuplicateGroupKeys = mutableSetOf<String>()
    private val queuedDuplicateDialogs = ArrayDeque<DuplicateMangaDialogState>()
    private val duplicateWatcherJob = presenterScope.launch {
        // The selected-category flow already observes the whole library when "All" is active.
        // For a filtered category, watch the full library separately so additions outside the
        // category still trigger duplicate detection.
        selectedCategoryId
            .flatMapLatest { categoryId ->
                if (categoryId == SYSTEM_ALL_CATEGORY.id) {
                    emptyFlow()
                } else {
                    repository.observeLibrary(null)
                        .flowOn(Dispatchers.IO)
                        .catch { }
                }
            }
            .catch { }
            .collect { items -> detectDuplicates(items) }
    }
    private val layoutExtrasFlow = combine(
        isFilterDialogOpenState,
        duplicateDialogState,
    ) { filterOpen, duplicateDialog -> filterOpen to duplicateDialog }

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
                    val retained = selected?.takeIf { id -> result.items.any { it.id == id } }
                    if (selected != null && retained == null) {
                        detailMangaId.compareAndSet(selected, null)
                    }
                    retained
                }
                if (selectedCategoryId.value == SYSTEM_ALL_CATEGORY.id) {
                    detectDuplicates(result.items)
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
            layoutExtrasFlow,
        ) { selId, mode, size, selState, (filterOpen, duplicateDialog) ->
            LayoutAndSelectionData(selId, mode, size, selState, filterOpen, duplicateDialog)
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
                duplicateDialog = layout.duplicateDialog,
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
                duplicateDialog = layout.duplicateDialog,
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
                    duplicateDialog = layout.duplicateDialog,
                )
            }
        }
    }.stateIn(
        scope = presenterScope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(),
    )

    private val selectedRepositoryState: Flow<SelectedRepositoryState> = combine(
        detailMangaId,
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
                detailMangaId.compareAndSet(result.selectedId, null)
                selectedMangaId.compareAndSet(result.selectedId, null)
            }
        }

    private val chapterSettingsOverrides = MutableStateFlow(ChapterSettingsOverrides())
    private val isEditInfoDialogOpenState = MutableStateFlow(false)
    private val isChapterSettingsDialogOpenState = MutableStateFlow(false)

    val detailState: StateFlow<MangaDetailUiState> = combine(
        selectedRepositoryState,
        chapterSettingsOverrides,
        isEditInfoDialogOpenState,
        isChapterSettingsDialogOpenState,
    ) { result, overrides, isEditInfoOpen, isChapterSettingsOpen ->
        when (result) {
            SelectedRepositoryState.Loading -> MangaDetailUiState(
                loading = true,
                isChapterSettingsDialogOpen = isChapterSettingsOpen,
            )
            is SelectedRepositoryState.Failed -> MangaDetailUiState(
                errorMessage = result.message,
                isChapterSettingsDialogOpen = isChapterSettingsOpen,
            )
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

                val persistedSettings = manga?.toChapterSettings() ?: defaultChapterSettings()
                val overrideSettings = overrides.settings
                val settings = if (manga != null && overrides.mangaId == manga.id && overrideSettings != null) {
                    overrideSettings
                } else {
                    persistedSettings
                }
                val filter = settings.toFilterState()
                val scanlatorFiltered = if (settings.excludedScanlators.isEmpty()) {
                    rawChapters
                } else {
                    rawChapters.filter { chapter ->
                        val scanlator = chapter.scanlator?.trim()
                        scanlator.isNullOrBlank() || scanlator !in settings.excludedScanlators
                    }
                }
                val filtered = scanlatorFiltered.filter { chapter ->
                    matchesChapterFilter(chapter, filter, downloadedIds)
                }
                val sorted = sortChapters(filtered, settings)
                val readerChapters = sortChapters(scanlatorFiltered, settings)
                val availableScanlators = rawChapters.mapNotNull { chapter ->
                    chapter.scanlator?.trim()?.takeIf(String::isNotEmpty)
                }.toSet()

                MangaDetailUiState(
                    manga = manga,
                    chapters = sorted,
                    allChapters = rawChapters,
                    readerChapters = readerChapters,
                    readerAvailability = result.readerAvailability,
                    chapterFilterState = filter,
                    chapterSortState = settings.toSortState(),
                    chapterSettings = settings,
                    availableScanlators = availableScanlators,
                    chapterListItems = buildChapterListItems(sorted, settings),
                    isEditInfoDialogOpen = isEditInfoOpen,
                    isChapterSettingsDialogOpen = isChapterSettingsOpen,
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
        val mangaId = detailMangaId.value ?: return
        updateChapterSettings(mangaId) { current ->
            current.copy(
                unreadFilter = filter.unread,
                downloadedFilter = filter.downloaded,
                bookmarkedFilter = filter.bookmarked,
            )
        }
    }

    fun setChapterSort(sort: ChapterSortState) {
        val mangaId = detailMangaId.value ?: return
        updateChapterSettings(mangaId) { current ->
            current.copy(sortMode = sort.mode, sortAscending = sort.ascending)
        }
    }

    fun setChapterDisplayMode(mode: ChapterDisplayMode) {
        val mangaId = detailMangaId.value ?: return
        updateChapterSettings(mangaId) { it.copy(displayMode = mode) }
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        val mangaId = detailMangaId.value ?: return
        updateChapterSettings(mangaId) { it.copy(excludedScanlators = excludedScanlators) }
    }

    fun setShowMissingChapters(show: Boolean) {
        val mangaId = detailMangaId.value ?: return
        updateChapterSettings(mangaId) { it.copy(showMissingChapters = show) }
    }

    fun setChapterSettingsDialogOpen(open: Boolean) {
        isChapterSettingsDialogOpenState.value = open
    }

    fun setChapterSettingsAsDefault(applyToExisting: Boolean) {
        val mangaId = detailMangaId.value ?: return
        val settings = currentChapterSettings(mangaId)
        preferences?.update {
            setProperty(CHAPTER_DEFAULT_FLAGS_KEY, encodeChapterFlags(0L, settings).toString())
            setProperty(CHAPTER_DEFAULT_SHOW_MISSING_KEY, settings.showMissingChapters.toString())
        }
        if (applyToExisting) {
            val now = System.currentTimeMillis()
            val records = repository.allMangaSnapshot().filter { it.favorite }
            mutationPort?.transaction {
                for (record in records) {
                    updateManga(
                        record.copy(
                            chapterFlags = encodeChapterFlags(record.chapterFlags, settings),
                            memoJson = encodeShowMissingChapters(record.memoJson, settings.showMissingChapters),
                        ),
                    )
                }
            }
            detailRetryRequest.value = now
        }
    }

    fun resetChapterSettingsToDefault() {
        val mangaId = detailMangaId.value ?: return
        val defaults = defaultChapterSettings().copy(excludedScanlators = emptySet())
        chapterSettingsOverrides.update { it.copy(mangaId = mangaId, settings = defaults) }
        persistChapterSettings(mangaId, defaults)
    }

    fun dismissDuplicateDialog() {
        advanceDuplicateDialog(markDismissed = true)
    }

    fun addDuplicateAnyway() {
        advanceDuplicateDialog(markDismissed = true)
    }

    fun openDuplicateManga(mangaId: Long) {
        advanceDuplicateDialog(markDismissed = true)
        // Duplicate candidates come from the full library, so make sure a filtered category
        // cannot immediately clear the requested selection.
        selectedCategoryId.value = SYSTEM_ALL_CATEGORY.id
        selectedMangaId.value = mangaId
        detailMangaId.value = mangaId
    }

    fun migrateDuplicateTo(existingMangaId: Long) {
        val dialog = duplicateDialogState.value ?: return
        val sourceMangaId = dialog.target.id
        if (sourceMangaId != existingMangaId) {
            migrateManga(sourceMangaId, existingMangaId)
        }
        advanceDuplicateDialog(markDismissed = true)
        retry()
    }

    private fun updateChapterSettings(mangaId: Long, transform: (ChapterSettings) -> ChapterSettings) {
        val updated = transform(currentChapterSettings(mangaId))
        chapterSettingsOverrides.update { it.copy(mangaId = mangaId, settings = updated) }
        persistChapterSettings(mangaId, updated)
    }

    private fun currentChapterSettings(mangaId: Long): ChapterSettings {
        val persisted = runCatching { repository.mangaSnapshot(mangaId) }.getOrNull()?.toChapterSettings()
            ?: detailState.value.manga?.takeIf { it.id == mangaId }?.toChapterSettings()
            ?: defaultChapterSettings()
        val overrides = chapterSettingsOverrides.value
        val overrideSettings = overrides.settings
        return if (overrides.mangaId == mangaId && overrideSettings != null) overrideSettings else persisted
    }

    private fun persistChapterSettings(mangaId: Long, settings: ChapterSettings) {
        val record = runCatching { repository.mangaSnapshot(mangaId) }.getOrNull()
            ?: detailState.value.manga?.takeIf { it.id == mangaId }
            ?: return
        val now = System.currentTimeMillis()
        mutationPort?.updateManga(
            record.toMangaRecord().copy(
                chapterFlags = encodeChapterFlags(record.chapterFlags, settings),
                excludedScanlatorsJson = encodeExcludedScanlators(settings.excludedScanlators),
                memoJson = encodeShowMissingChapters(record.memoJson, settings.showMissingChapters),
            ),
        )
        detailRetryRequest.value = now
    }

    private fun defaultChapterSettings(): ChapterSettings {
        val flags = preferences?.property(CHAPTER_DEFAULT_FLAGS_KEY)?.toLongOrNull() ?: 0L
        val showMissing = preferences?.property(CHAPTER_DEFAULT_SHOW_MISSING_KEY)?.toBooleanStrictOrNull() ?: true
        return chapterSettingsFromFlags(chapterFlags = flags, showMissingChapters = showMissing)
    }

    private fun detectDuplicates(items: List<LibraryManga>) {
        synchronized(duplicateLock) {
            val currentIds = items.mapTo(mutableSetOf()) { it.id }
            val newIds = currentIds - knownMangaIds
            knownMangaIds.addAll(currentIds)
            if (items.isEmpty()) return

            findDuplicateGroups(items).forEach { (normalizedTitle, members) ->
                val groupKey = normalizedTitle + "|" + members.map { it.id }.sorted().joinToString(",")
                if (groupKey in dismissedDuplicateGroupKeys) return@forEach
                if (duplicateDialogState.value?.groupKey == groupKey) return@forEach
                if (queuedDuplicateDialogs.any { it.groupKey == groupKey }) return@forEach

                val target = members.firstOrNull { it.id in newIds } ?: members.first()
                val candidates = members
                    .filter { it.id != target.id && it.sourceId != target.sourceId }
                    .map { it.toDuplicateCandidate() }
                if (candidates.isEmpty()) return@forEach

                val dialog = DuplicateMangaDialogState(
                    groupKey = groupKey,
                    target = target.toDuplicateCandidate(),
                    candidates = candidates,
                )
                if (duplicateDialogState.value == null) {
                    duplicateDialogState.value = dialog
                } else {
                    queuedDuplicateDialogs.addLast(dialog)
                }
            }
        }
    }

    private fun advanceDuplicateDialog(markDismissed: Boolean) {
        synchronized(duplicateLock) {
            val current = duplicateDialogState.value ?: return
            if (markDismissed) {
                dismissedDuplicateGroupKeys += current.groupKey
            }
            duplicateDialogState.value = queuedDuplicateDialogs.removeFirstOrNull()
        }
    }

    private fun migrateManga(sourceMangaId: Long, targetMangaId: Long) {
        val port = mutationPort ?: return
        val source = repository.mangaSnapshot(sourceMangaId) ?: return
        repository.mangaSnapshot(targetMangaId) ?: return
        val sourceChapters = repository.chapterSnapshot(sourceMangaId)
        val targetChaptersByUrl = repository.chapterSnapshot(targetMangaId).associateBy { it.url }
        val now = System.currentTimeMillis()
        port.transaction {
            for (chapter in sourceChapters) {
                val existing = targetChaptersByUrl[chapter.url]
                if (existing == null) {
                    updateChapter(chapter.copy(mangaId = targetMangaId, lastModifiedAt = now).toChapterRecord())
                } else {
                    updateChapter(
                        existing.copy(
                            read = existing.read || chapter.read,
                            bookmark = existing.bookmark || chapter.bookmark,
                            lastPageRead = maxOf(existing.lastPageRead, chapter.lastPageRead),
                            lastModifiedAt = now,
                        ).toChapterRecord(),
                    )
                }
            }

            val sourceCategoryIds = repository.mangaCategoryLinksSnapshot()[sourceMangaId].orEmpty()
            val targetCategoryIds = repository.mangaCategoryLinksSnapshot()[targetMangaId].orEmpty().toSet()
            sourceCategoryIds.filterNot { it in targetCategoryIds }.forEach { categoryId ->
                linkCategory(targetMangaId, categoryId)
            }

            for (tracking in repository.trackingSnapshot(sourceMangaId)) {
                if (findTracking(targetMangaId, tracking.trackerId) == null) {
                    insertTracking(tracking.copy(id = 0L, mangaId = targetMangaId))
                }
                deleteTracking(sourceMangaId, tracking.trackerId)
            }

            updateManga(
                source.toMangaRecord().copy(
                    favorite = false,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                ),
            )
        }
    }

    fun setEditInfoDialogOpen(open: Boolean) {
        isEditInfoDialogOpenState.value = open
    }

    fun toggleChapterBookmark(chapterId: Long) {
        val mangaId = detailMangaId.value ?: return
        val currentChapters = repository.chapterSnapshot(mangaId)
        val chapter = currentChapters.find { it.id == chapterId } ?: return
        val updated = chapter.copy(bookmark = !chapter.bookmark)
        mutationPort?.updateChapter(updated.toChapterRecord())
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun toggleChapterRead(chapterId: Long) {
        val mangaId = detailMangaId.value ?: return
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

    fun batchBookmarkChapters(chapterIds: Set<Long>, bookmark: Boolean) {
        val mangaId = detailMangaId.value ?: return
        val currentChapters = repository.chapterSnapshot(mangaId)
        val toUpdate = currentChapters.filter { it.id in chapterIds && it.bookmark != bookmark }
        if (toUpdate.isEmpty()) return
        mutationPort?.transaction {
            toUpdate.forEach { ch ->
                updateChapter(ch.copy(bookmark = bookmark).toChapterRecord())
            }
        }
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun batchMarkChaptersRead(chapterIds: Set<Long>, read: Boolean) {
        val mangaId = detailMangaId.value ?: return
        val currentChapters = repository.chapterSnapshot(mangaId)
        val toUpdate = currentChapters.filter { it.id in chapterIds && it.read != read }
        if (toUpdate.isEmpty()) return
        val now = System.currentTimeMillis()
        mutationPort?.transaction {
            toUpdate.forEach { ch ->
                updateChapter(
                    ch.copy(
                        read = read,
                        lastPageRead = if (!read) 0L else ch.lastPageRead,
                        lastModifiedAt = now,
                    ).toChapterRecord(),
                )
            }
        }
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun batchDownloadChapters(chapterIds: Set<Long>) {
        val manga = detailState.value.manga ?: return
        val chapters = detailState.value.allChapters.filter { it.id in chapterIds }
        if (chapters.isEmpty()) return
        downloadChapters(chapters)
    }

    fun batchDeleteChapterDownloads(chapterIds: Set<Long>) {
        val manga = detailState.value.manga ?: return
        val chapters = detailState.value.allChapters.filter { it.id in chapterIds }
        if (chapters.isEmpty()) return
        chapters.forEach { ch ->
            downloader?.diskProvider?.deleteChapter(manga.sourceId, manga.title, ch.name)
        }
        detailRetryRequest.value = System.currentTimeMillis()
    }

    fun markPreviousChaptersRead(chapterId: Long) {
        val mangaId = detailMangaId.value ?: return
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
        downloadChapters(listOf(ch))
    }

    fun downloadChapters(chapters: List<LibraryChapter>) {
        val manga = detailState.value.manga ?: return
        if (chapters.isEmpty()) return
        presenterScope.launch {
            downloader?.enqueue(
                sourceId = manga.sourceId,
                mangaId = manga.id,
                mangaTitle = manga.title,
                chapters = chapters,
            )
        }
    }

    fun downloadNextChapters(amount: Int?, unreadOnly: Boolean = true) {
        val all = detailState.value.allChapters
        val sorted = all.sortedBy { it.sourceOrder }
        val target = if (unreadOnly) sorted.filter { !it.read } else sorted
        val toDownload = if (amount != null && amount > 0) target.take(amount) else target
        downloadChapters(toDownload)
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
        val id = detailMangaId.value ?: return
        updateMangaInfo(id, title, author, artist, description, genres, status, notes)
    }

    fun resetSelectedMangaInfo() {
        val id = detailMangaId.value ?: return
        resetMangaInfo(id)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectManga(id: Long?) {
        val selected = id?.takeIf { candidate -> state.value.items.any { it.id == candidate } }
        selectedMangaId.value = selected
        detailMangaId.value = selected
    }

    fun openMangaDetail(id: Long) {
        detailMangaId.value = id
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
        duplicateWatcherJob.cancel()
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
    val duplicateDialog: DuplicateMangaDialogState?,
)

private data class ChapterSettingsOverrides(
    val mangaId: Long? = null,
    val settings: ChapterSettings? = null,
)

private fun matchesChapterFilter(
    chapter: LibraryChapter,
    filter: ChapterFilterState,
    downloadedChapterIds: Set<Long>,
): Boolean {
    val unreadMatch = when (filter.unread) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> !chapter.read
        TriStateFilter.Exclude -> chapter.read
    }
    val bookmarkMatch = when (filter.bookmarked) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> chapter.bookmark
        TriStateFilter.Exclude -> !chapter.bookmark
    }
    val downloadedMatch = when (filter.downloaded) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> chapter.id in downloadedChapterIds
        TriStateFilter.Exclude -> chapter.id !in downloadedChapterIds
    }
    return unreadMatch && bookmarkMatch && downloadedMatch
}

private fun sortChapters(
    chapters: List<LibraryChapter>,
    settings: ChapterSettings,
): List<LibraryChapter> {
    val comparator: Comparator<LibraryChapter> = when (settings.sortMode) {
        ChapterSortMode.SourceOrder -> compareBy { it.sourceOrder }
        ChapterSortMode.ChapterNumber -> compareBy { it.chapterNumber }
        ChapterSortMode.UploadDate -> compareBy { it.dateUpload }
    }
    return if (settings.sortAscending) {
        chapters.sortedWith(comparator)
    } else {
        chapters.sortedWith(comparator.reversed())
    }
}

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
    val localAsset = readerLibrary?.chapterAsset(id)
    if (localAsset != null && localAsset.hasReadableContent()) return ChapterReaderAvailability.Readable
    if (readerLibrary?.onlineChapter(id) != null) return ChapterReaderAvailability.Readable
    return if (localAsset != null) {
        ChapterReaderAvailability.MissingLocalContent
    } else {
        ChapterReaderAvailability.RemoteOnly
    }
}

private fun ReaderChapterAsset.hasReadableContent(): Boolean {
    val path = storageRoot.resolve(relativePath)
    return if (assetKind == "DIRECTORY") {
        Files.isDirectory(path)
    } else {
        Files.isRegularFile(path)
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

private fun MangaDetails.toMangaRecord(): MangaRecord = MangaRecord(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genreJson = genreJson,
    status = status,
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    updateStrategy = updateStrategy,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    excludedScanlatorsJson = excludedScanlatorsJson,
    version = version,
    notes = notes,
    initialized = initialized,
    memoJson = memoJson,
)
