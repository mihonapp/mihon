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
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryRepository
import java.nio.file.Files

data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<LibraryManga> = emptyList(),
    val selectedMangaId: Long? = null,
    val errorMessage: String? = null,
)

data class MangaDetailUiState(
    val manga: MangaDetails? = null,
    val chapters: List<LibraryChapter> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val readerAvailability: Map<Long, ChapterReaderAvailability> = emptyMap(),
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
) : AutoCloseable {
    private val presenterJob = SupervisorJob(scope.coroutineContext[Job])
    private val presenterScope = CoroutineScope(scope.coroutineContext + presenterJob)
    private val query = MutableStateFlow("")
    private val selectedMangaId = MutableStateFlow<Long?>(null)
    private val retryRequest = MutableStateFlow(0L)
    private val detailRetryRequest = MutableStateFlow(0L)

    private val repositoryState: Flow<RepositoryState> = retryRequest
        .flatMapLatest {
            flow { emitAll(repository.observeLibrary()) }
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

    val state: StateFlow<LibraryUiState> = combine(repositoryState, query, selectedMangaId) { result, query, selected ->
        when (result) {
            RepositoryState.Loading -> LibraryUiState(loading = true, query = query)
            is RepositoryState.Failed -> LibraryUiState(
                loading = false,
                query = query,
                errorMessage = result.message,
            )
            is RepositoryState.Loaded -> {
                val normalizedQuery = query.trim()
                val visibleItems = if (normalizedQuery.isEmpty()) {
                    result.items
                } else {
                    result.items.filter { manga ->
                        manga.title.contains(normalizedQuery, ignoreCase = true) ||
                            manga.author?.contains(normalizedQuery, ignoreCase = true) == true
                    }
                }
                LibraryUiState(
                    loading = false,
                    query = query,
                    items = visibleItems,
                    selectedMangaId = selected?.takeIf { id -> result.items.any { it.id == id } },
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

    val detailState: StateFlow<MangaDetailUiState> = selectedRepositoryState
        .map { result ->
            when (result) {
                SelectedRepositoryState.Loading -> MangaDetailUiState(loading = true)
                is SelectedRepositoryState.Failed -> MangaDetailUiState(errorMessage = result.message)
                is SelectedRepositoryState.Loaded -> MangaDetailUiState(
                    manga = result.manga,
                    chapters = result.chapters,
                    readerAvailability = result.readerAvailability,
                )
            }
        }
        .stateIn(
            scope = presenterScope,
            started = SharingStarted.Eagerly,
            initialValue = MangaDetailUiState(),
        )

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectManga(id: Long?) {
        selectedMangaId.value = id?.takeIf { selected -> state.value.items.any { it.id == selected } }
    }

    fun retry() {
        retryRequest.update { it + 1 }
    }

    fun retryDetail() {
        detailRetryRequest.update { it + 1 }
    }

    override fun close() {
        presenterScope.cancel()
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
    val asset = readerLibrary?.chapterAsset(id) ?: return ChapterReaderAvailability.RemoteOnly
    return if (Files.isRegularFile(asset.storageRoot.resolve(asset.relativePath))) {
        ChapterReaderAvailability.Readable
    } else {
        ChapterReaderAvailability.MissingLocalContent
    }
}
