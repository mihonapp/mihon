package eu.kanade.presentation.updates.failed

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.failed.repository.FailedUpdatesRepository
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class FailedUpdatesScreenModel(
    private val context: Context,
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val failedUpdatesManager: FailedUpdatesRepository = Injekt.get(),
) : StateScreenModel<FailedUpdatesScreenState>(FailedUpdatesScreenState()) {
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedMangaIds: HashSet<Long> = HashSet()
    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            val sortMode = preferenceStore.getEnum("sort_mode", SortingMode.BY_ALPHABET).get()
            combine(
                getSourcesWithFavoriteCount.subscribe(),
                getLibraryManga.subscribe(),
                failedUpdatesManager.getFailedUpdates(),
                getCategories.subscribe(),
            ) { sources, libraryManga, failedUpdates, categories ->
                Triple(sources, libraryManga, failedUpdates) to categories
            }
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { (triple, categories) ->
                    val (sources, libraryManga, failedUpdates) = triple
                    mutableState.update { state ->
                        val categoriesMap = categories.associateBy { group -> group.id }
                        state.copy(
                            sourcesCount = sources,
                            items = libraryManga
                                .distinctBy { it.manga.id }
                                .filter { libraryManga ->
                                    failedUpdates.any { it.mangaId == libraryManga.manga.id }
                                }
                                .map { libraryManga ->
                                    // Untrusted Extensions cause null crash
                                    val source = sourceManager.getOrStub(libraryManga.manga.source)
                                    val failedUpdate = failedUpdates.find { it.mangaId == libraryManga.manga.id }!!
                                    val errorMessage = failedUpdate.errorMessage
                                    FailedUpdatesManga(
                                        libraryManga = libraryManga,
                                        errorMessage = errorMessage,
                                        selected = libraryManga.id in selectedMangaIds,
                                        source = source,
                                        category = categoriesMap[libraryManga.category]!!,
                                    )
                                },
                            groupByMode = preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).get(),
                            sortMode = sortMode,
                            descendingOrder = preferenceStore.getBoolean("descending_order", false).get(),
                            isLoading = false,
                        )
                    }
                }
            runSortAction(sortMode)
        }
    }

    fun runSortAction(mode: SortingMode) {
        when (mode) {
            SortingMode.BY_ALPHABET -> sortByAlphabet()
        }
    }

    fun runGroupBy(mode: GroupByMode) {
        when (mode) {
            GroupByMode.NONE -> unGroup()
            GroupByMode.BY_SOURCE -> groupBySource()
        }
    }

    private fun sortByAlphabet() {
        mutableState.update { state ->
            val descendingOrder = if (state.sortMode == SortingMode.BY_ALPHABET) !state.descendingOrder else false
            preferenceStore.getBoolean("descending_order", false).set(descendingOrder)
            state.copy(
                items = if (descendingOrder) {
                    state.items.sortedByDescending {
                        it.libraryManga.manga.title
                    }
                } else {
                    state.items.sortedBy { it.libraryManga.manga.title }
                },
                descendingOrder = descendingOrder,
                sortMode = SortingMode.BY_ALPHABET,
            )
        }
        preferenceStore.getEnum("sort_mode", SortingMode.BY_ALPHABET).set(SortingMode.BY_ALPHABET)
    }

    @Composable
    fun categoryMap(
        items: List<FailedUpdatesManga>,
        groupMode: GroupByMode,
        sortMode: SortingMode,
        descendingOrder: Boolean,
    ): Map<String, Map<String, List<FailedUpdatesManga>>> {
        val unsortedMap = when (groupMode) {
            GroupByMode.BY_SOURCE -> items.groupBy { it.source.name }
                .mapValues { entry -> entry.value.groupBy { it.errorMessage } }
            GroupByMode.NONE -> emptyMap()
        }
        return when (sortMode) {
            SortingMode.BY_ALPHABET -> {
                val sortedMap =
                    TreeMap<String, Map<String, List<FailedUpdatesManga>>>(
                        if (descendingOrder) {
                            compareByDescending { it }
                        } else {
                            compareBy { it }
                        },
                    )
                sortedMap.putAll(unsortedMap)
                sortedMap
            }
        }
    }

    fun updateExpandedMap(key: GroupKey, value: Boolean) {
        mutableState.update { it.copy(expanded = it.expanded + (key to value)) }
    }

    fun initializeExpandedMap(categoryMap: Map<String, Map<String, List<FailedUpdatesManga>>>) {
        mutableState.update { currentState ->
            val newMap = mutableMapOf<GroupKey, Boolean>()
            newMap.putAll(
                categoryMap.keys.flatMap { source ->
                    listOf(GroupKey(source, "") to false)
                } + categoryMap.flatMap { entry ->
                    entry.value.keys.map { errorMessage ->
                        GroupKey(entry.key, errorMessage) to false
                    }
                },
            )
            currentState.copy(expanded = newMap)
        }
    }

    fun expandAll() {
        val newExpanded = mutableState.value.expanded.mapValues { true }
        mutableState.update { it.copy(expanded = newExpanded) }
    }

    fun contractAll() {
        val newExpanded = mutableState.value.expanded.mapValues { false }
        mutableState.update { it.copy(expanded = newExpanded) }
    }

    private fun groupBySource() {
        mutableState.update {
            it.copy(
                groupByMode = GroupByMode.BY_SOURCE,
            )
        }
        preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).set(GroupByMode.BY_SOURCE)
    }

    private fun unGroup() {
        mutableState.update {
            it.copy(
                groupByMode = GroupByMode.NONE,
            )
        }
        preferenceStore.getEnum("group_by_mode", GroupByMode.NONE).set(GroupByMode.NONE)
    }

    fun toggleSelection(
        item: FailedUpdatesManga,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
        groupByErrorMessage: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.items.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.libraryManga.manga.id == item.libraryManga.manga.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedMangaIds.addOrRemove(item.libraryManga.manga.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1 until selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1) until selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            range = IntRange.EMPTY
                        }

                        if (groupByErrorMessage) {
                            val firstErrorMessage = getOrNull(selectedPositions[0])?.errorMessage
                            val lastErrorMessage = getOrNull(selectedPositions[1])?.errorMessage

                            range.forEach {
                                val inBetweenItem = getOrNull(it)
                                if (inBetweenItem != null &&
                                    !inBetweenItem.selected &&
                                    inBetweenItem.errorMessage == firstErrorMessage &&
                                    inBetweenItem.errorMessage == lastErrorMessage
                                ) {
                                    selectedMangaIds.add(inBetweenItem.libraryManga.manga.id)
                                    set(it, inBetweenItem.copy(selected = true))
                                }
                            }
                        } else {
                            range.forEach {
                                val inBetweenItem = get(it)
                                if (!inBetweenItem.selected) {
                                    selectedMangaIds.add(inBetweenItem.libraryManga.manga.id)
                                    set(it, inBetweenItem.copy(selected = true))
                                }
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            state.copy(items = newItems)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedMangaIds.addOrRemove(it.libraryManga.manga.id, selected)
                it.copy(selected = selected)
            }
            state.copy(items = newItems)
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun removeMangas(mangaList: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
        if (deleteFromLibrary) {
            val set = mangaList.map { it.id }.toHashSet()
            mutableState.update { state ->
                state.copy(
                    items = state.items.filterNot { it.libraryManga.manga.id in set },
                )
            }
        }
    }

    fun dismissManga(selected: List<FailedUpdatesManga>) {
        val set = selected.map { it.libraryManga.manga.id }.toHashSet()
        val listOfMangaIds = selected.map { it.libraryManga.manga.id }
        toggleAllSelection(false)
        mutableState.update { state ->
            state.copy(
                items = state.items.filterNot { it.libraryManga.manga.id in set },
            )
        }

        screenModelScope.launchNonCancellable { failedUpdatesManager.removeFailedUpdatesByMangaIds(listOfMangaIds) }
    }

    fun openDeleteMangaDialog(selected: List<FailedUpdatesManga>) {
        val mangaList = selected.map { it.libraryManga.manga }
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(mangaList)) }
    }

    fun openErrorMessageDialog(errorMessage: String) {
        mutableState.update { it.copy(dialog = Dialog.ShowErrorMessage(errorMessage)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedMangaIds.addOrRemove(it.libraryManga.manga.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(items = newItems)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun groupSelection(items: List<FailedUpdatesManga>) {
        val newSelected = items.map { manga -> manga.libraryManga.id }.toHashSet()
        selectedMangaIds.addAll(newSelected)
        mutableState.update { state ->
            val newItems = state.items.map {
                it.copy(selected = if (it.libraryManga.id in newSelected) !it.selected else it.selected)
            }
            state.copy(items = newItems)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }
}

enum class GroupByMode {
    NONE,
    BY_SOURCE,
}

enum class SortingMode {
    BY_ALPHABET,
}

sealed class Dialog {
    data class DeleteManga(val manga: List<Manga>) : Dialog()

    data class ShowErrorMessage(val errorMessage: String) : Dialog()
}

sealed class Event {
    data object FailedFetchingSourcesWithCount : Event()
}

@Immutable
data class FailedUpdatesManga(
    val libraryManga: LibraryManga,
    val errorMessage: String,
    val selected: Boolean = false,
    val source: eu.kanade.tachiyomi.source.Source,
    val category: Category,
)

@Immutable
data class FailedUpdatesScreenState(
    val isLoading: Boolean = true,
    val items: List<FailedUpdatesManga> = emptyList(),
    val groupByMode: GroupByMode = GroupByMode.NONE,
    val sortMode: SortingMode = SortingMode.BY_ALPHABET,
    val descendingOrder: Boolean = false,
    val dialog: Dialog? = null,
    val sourcesCount: List<Pair<Source, Long>> = emptyList(),
    val expanded: Map<GroupKey, Boolean> = emptyMap(),
) {
    val selected = items.filter { it.selected }
    val selectionMode = selected.isNotEmpty()
}

data class GroupKey(
    val categoryOrSource: String,
    val errorMessage: String,
)
