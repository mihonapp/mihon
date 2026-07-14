package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class HistoryScreenModel(
    private val addTracks: AddTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val removeHistory: RemoveHistory = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<HistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedHistoryIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launch {
            state.map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    getHistory.subscribe(query ?: "")
                        .distinctUntilChanged()
                        .catch { error ->
                            logcat(LogPriority.ERROR, error)
                            _events.send(Event.InternalError)
                        }
                        .map { it.toHistoryUiModels() }
                        .flowOn(Dispatchers.IO)
                }
                .collect { newList ->
                    val visibleHistoryIds = newList.asHistoryItems().mapTo(HashSet()) { it.id }
                    selectedHistoryIds.retainAll(visibleHistoryIds)
                    mutableState.update { it.copy(list = newList, selection = selectedHistoryIds.toSet()) }
                }
        }
    }

    private fun List<HistoryWithRelations>.toHistoryUiModels(): List<HistoryUiModel> {
        return map { HistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    private fun List<HistoryUiModel>.asHistoryItems(): List<HistoryWithRelations> {
        return mapNotNull { (it as? HistoryUiModel.Item)?.item }
    }

    suspend fun getNextChapter(): Chapter? {
        return withIOContext { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        screenModelScope.launchIO {
            sendNextChapterEvent(getNextChapters.await(mangaId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(mangaId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(mangaId)
        }
    }

    fun removeSelectedFromHistory(history: List<HistoryWithRelations>) {
        screenModelScope.launchIO {
            removeHistory.await(history)
            toggleAllSelection(false)
        }
    }

    fun removeAllSelectedMangaFromHistory(history: List<HistoryWithRelations>) {
        screenModelScope.launchIO {
            history
                .map { it.mangaId }
                .distinct()
                .forEach { removeHistory.await(it) }
            toggleAllSelection(false)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun toggleSelection(
        item: HistoryWithRelations,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        val historyItems = state.value.list?.asHistoryItems() ?: return
        val selectedIndex = historyItems.indexOfFirst { it.id == item.id }
        if (selectedIndex < 0) return

        if ((item.id in selectedHistoryIds) == selected) return

        val firstSelection = selectedHistoryIds.isEmpty()
        selectedHistoryIds.addOrRemove(item.id, selected)

        if (selected && fromLongPress) {
            if (firstSelection) {
                selectedPositions[0] = selectedIndex
                selectedPositions[1] = selectedIndex
            } else {
                val range = when {
                    selectedIndex < selectedPositions[0] -> {
                        (selectedIndex + 1)..<selectedPositions[0]
                    }
                    selectedIndex > selectedPositions[1] -> {
                        (selectedPositions[1] + 1)..<selectedIndex
                    }
                    else -> IntRange.EMPTY
                }

                if (selectedIndex < selectedPositions[0]) {
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    selectedPositions[1] = selectedIndex
                }

                range.forEach {
                    selectedHistoryIds.add(historyItems[it].id)
                }
            }
        } else if (!fromLongPress) {
            val selectedIndices = historyItems
                .mapIndexedNotNull { index, history -> index.takeIf { history.id in selectedHistoryIds } }
            selectedPositions[0] = selectedIndices.firstOrNull() ?: -1
            selectedPositions[1] = selectedIndices.lastOrNull() ?: -1
        }

        mutableState.update { it.copy(selection = selectedHistoryIds.toSet()) }
    }

    fun toggleAllSelection(selected: Boolean) {
        val historyItems = state.value.list?.asHistoryItems().orEmpty()
        if (selected) {
            selectedHistoryIds.addAll(historyItems.map { it.id })
        } else {
            selectedHistoryIds.clear()
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
        mutableState.update { it.copy(selection = selectedHistoryIds.toSet()) }
    }

    fun invertSelection() {
        val historyItems = state.value.list?.asHistoryItems().orEmpty()
        historyItems.forEach {
            selectedHistoryIds.addOrRemove(it.id, it.id !in selectedHistoryIds)
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
        mutableState.update { it.copy(selection = selectedHistoryIds.toSet()) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun addFavorite(mangaId: Long) {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId) ?: return@launchIO

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                mutableState.update { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                return@launchIO
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
        }
    }

    fun addFavorites(history: List<HistoryWithRelations>) {
        screenModelScope.launchIO {
            val manga = history
                .mapNotNull { getManga.await(it.mangaId) }
                .filterNot { it.favorite }
                .distinctBy { it.id }
            if (manga.isEmpty()) {
                toggleAllSelection(false)
                return@launchIO
            }

            manga.firstOrNull { getDuplicateLibraryManga(it).isNotEmpty() }?.let { duplicate ->
                mutableState.update {
                    it.copy(dialog = Dialog.DuplicateManga(duplicate, getDuplicateLibraryManga(duplicate)))
                }
                return@launchIO
            }

            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            val categoryIds = when {
                defaultCategory != null -> listOf(defaultCategory.id)
                defaultCategoryId == 0L || categories.isEmpty() -> emptyList()
                else -> {
                    mutableState.update { currentState ->
                        currentState.copy(
                            dialog = Dialog.ChangeCategoryForSelected(
                                history = history,
                                initialSelection = categories.mapAsCheckboxState { false },
                            ),
                        )
                    }
                    return@launchIO
                }
            }

            addFavoritesToCategories(manga, categoryIds)
        }
    }

    fun addFavoritesToCategories(history: List<HistoryWithRelations>, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            val manga = history
                .mapNotNull { getManga.await(it.mangaId) }
                .filterNot { it.favorite }
                .distinctBy { it.id }
                .filter { getDuplicateLibraryManga(it).isEmpty() }
            addFavoritesToCategories(manga, categoryIds)
        }
    }

    private suspend fun addFavoritesToCategories(manga: List<Manga>, categoryIds: List<Long>) {
        val now = Instant.now().toEpochMilli()
        val updates = manga.map {
            MangaUpdate(
                id = it.id,
                favorite = true,
                dateAdded = now,
            )
        }
        val result = updateManga.awaitAll(updates)
        if (!result) return

        manga.forEach {
            setMangaCategories.await(it.id, categoryIds)
            addTracks.bindEnhancedTrackers(it, sourceManager.getOrStub(it.source))
        }
        toggleAllSelection(false)
    }

    fun showMigrateDialog(target: Manga, current: Manga) {
        mutableState.update { currentState ->
            currentState.copy(dialog = Dialog.Migrate(target = target, current = current))
        }
    }

    fun showChangeCategoryDialog(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            mutableState.update { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection },
                    ),
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<HistoryUiModel>? = null,
        val selection: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
    ) {
        val selected: List<HistoryWithRelations>
            get() = list.orEmpty()
                .mapNotNull { (it as? HistoryUiModel.Item)?.item?.takeIf { item -> item.id in selection } }

        val selectionMode: Boolean
            get() = selected.isNotEmpty()
    }

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: HistoryWithRelations) : Dialog
        data class DeleteSelected(val history: List<HistoryWithRelations>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class ChangeCategoryForSelected(
            val history: List<HistoryWithRelations>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
