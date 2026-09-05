package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class MigrateMangaViewModel(
    @Assisted private val sourceId: Long,
    private val sourceManager: SourceManager,
    private val getLibraryManga: GetLibraryManga,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    val getCategories: GetCategories,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): MigrateMangaViewModel
    }

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    private val source = viewModelScope.async { sourceManager.getOrStub(sourceId) }

    private val selection = MutableStateFlow(emptySet<Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val _filters = MutableStateFlow(MigrateMangaFilters())
    val filters: StateFlow<MigrateMangaFilters> = _filters.asStateFlow()

    private val hasActiveFilters = _filters
        .map { it.isActive }
        .distinctUntilChanged()

    private val favorites = combine(
        getLibraryManga.subscribe()
            .catch {
                logcat(LogPriority.ERROR, it)
                _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                emit(listOf())
            },
        downloadCache.changes,
        _filters,
    ) { libraryManga, _, filters ->
        libraryManga
            .asSequence()
            .filter { it.manga.source == sourceId }
            .applyFilters(filters)
            .map { it.manga }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .toList()
    }

    val state: StateFlow<State> = combine(
        favorites,
        selection,
        dialog,
        hasActiveFilters,
    ) { titleList, selection, dialog, hasActiveFilters ->
        State(
            source = source.await(),
            selection = selection,
            dialog = dialog,
            hasActiveFilters = hasActiveFilters,
            titleList = titleList,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun Sequence<LibraryManga>.applyFilters(filters: MigrateMangaFilters): Sequence<LibraryManga> {
        return this
            .filter { applyFilter(filters.downloaded) { downloadManager.getDownloadCount(it.manga) > 0 } }
            .filter { applyFilter(filters.unread) { it.unreadCount > 0 } }
            .filter { applyFilter(filters.started) { it.hasStarted } }
            .filter { applyFilter(filters.bookmarked) { it.hasBookmarks } }
            .filter { libraryManga ->
                val included = filters.includedCategories
                val excluded = filters.excludedCategories
                if (included.isEmpty() && excluded.isEmpty()) {
                    true
                } else {
                    val categories = libraryManga.categories
                    val isIncluded = included.isEmpty() || categories.any { it in included }
                    val isExcluded = excluded.isNotEmpty() && categories.any { it in excluded }
                    isIncluded && !isExcluded
                }
            }
    }

    fun toggleSelection(item: Manga) {
        selection.update { selection ->
            selection.mutate { list ->
                if (!list.remove(item.id)) list.add(item.id)
            }
        }
    }

    fun clearSelection() {
        selection.update { emptySet() }
    }

    fun showFilterDialog() {
        dialog.update { Dialog.Filter }
    }

    fun dismissDialog() {
        dialog.update { null }
    }

    fun toggleFilterDownloaded() = _filters.update { it.copy(downloaded = it.downloaded.next()) }

    fun toggleFilterUnread() = _filters.update { it.copy(unread = it.unread.next()) }

    fun toggleFilterStarted() = _filters.update { it.copy(started = it.started.next()) }

    fun toggleFilterBookmarked() = _filters.update { it.copy(bookmarked = it.bookmarked.next()) }

    fun cycleCategory(category: Category) {
        _filters.update { current ->
            when (category.id) {
                in current.includedCategories -> current.copy(
                    includedCategories = current.includedCategories - category.id,
                    excludedCategories = current.excludedCategories + category.id,
                )
                in current.excludedCategories -> current.copy(
                    excludedCategories = current.excludedCategories - category.id,
                )
                else -> current.copy(includedCategories = current.includedCategories + category.id)
            }
        }
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val selection: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
        val hasActiveFilters: Boolean = false,
        private val titleList: List<Manga>? = null,
    ) {

        val titles: List<Manga>
            get() = titleList ?: listOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode = selection.isNotEmpty()
    }

    sealed interface Dialog {
        data object Filter : Dialog
    }
}

@Immutable
data class MigrateMangaFilters(
    val downloaded: TriState = TriState.DISABLED,
    val unread: TriState = TriState.DISABLED,
    val started: TriState = TriState.DISABLED,
    val bookmarked: TriState = TriState.DISABLED,
    val includedCategories: Set<Long> = emptySet(),
    val excludedCategories: Set<Long> = emptySet(),
) {
    val isActive: Boolean
        get() = listOf(downloaded, unread, started, bookmarked).any { it != TriState.DISABLED } ||
            includedCategories.isNotEmpty() ||
            excludedCategories.isNotEmpty()
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
