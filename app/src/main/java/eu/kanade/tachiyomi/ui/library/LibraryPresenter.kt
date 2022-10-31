package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.history.interactor.GetNextUnreadChapters
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.library.model.LibrarySort
import eu.kanade.domain.library.model.sort
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.track.interactor.GetTracksPerManga
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.library.LibraryState
import eu.kanade.presentation.library.LibraryStateImpl
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Collator
import java.util.Collections
import java.util.Locale
import eu.kanade.tachiyomi.data.database.models.Manga as DbManga

/**
 * Class containing library information.
 */
private data class Library(val categories: List<Category>, val mangaMap: LibraryMap)

/**
 * Typealias for the library manga, using the category as keys, and list of manga as values.
 */
typealias LibraryMap = Map<Long, List<LibraryItem>>

class LibraryPresenter(
    private val state: LibraryStateImpl = LibraryState() as LibraryStateImpl,
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getNextUnreadChapters: GetNextUnreadChapters = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
) : BasePresenter<LibraryController>(), LibraryState by state {

    private var loadedManga by mutableStateOf(emptyMap<Long, List<LibraryItem>>())

    val isLibraryEmpty by derivedStateOf { loadedManga.isEmpty() }

    val tabVisibility by libraryPreferences.categoryTabs().asState()
    val mangaCountVisibility by libraryPreferences.categoryNumberOfItems().asState()

    val showDownloadBadges by libraryPreferences.downloadBadge().asState()
    val showUnreadBadges by libraryPreferences.unreadBadge().asState()
    val showLocalBadges by libraryPreferences.localBadge().asState()
    val showLanguageBadges by libraryPreferences.languageBadge().asState()

    var activeCategory: Int by libraryPreferences.lastUsedCategory().asState()

    val isDownloadOnly: Boolean by preferences.downloadedOnly().asState()
    val isIncognitoMode: Boolean by preferences.incognitoMode().asState()

    private val _filterChanges: Channel<Unit> = Channel(Int.MAX_VALUE)
    private val filterChanges = _filterChanges.receiveAsFlow().onStart { emit(Unit) }

    private var librarySubscription: Job? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        subscribeLibrary()
    }

    fun subscribeLibrary() {
        /**
         * TODO:
         * - Move filter and sort to getMangaForCategory and only filter and sort the current display category instead of whole library as some has 5000+ items in the library
         * - Create new db view and new query to just fetch the current category save as needed to instance variable
         * - Fetch badges to maps and retrieve as needed instead of fetching all of them at once
         */
        if (librarySubscription == null || librarySubscription!!.isCancelled) {
            librarySubscription = presenterScope.launchIO {
                combine(getLibraryFlow(), getTracksPerManga.subscribe(), filterChanges) { library, tracks, _ ->
                    library.mangaMap
                        .applyFilters(tracks)
                        .applySort(library.categories)
                }
                    .collectLatest {
                        state.isLoading = false
                        loadedManga = it
                    }
            }
        }
    }

    /**
     * Applies library filters to the given map of manga.
     */
    private fun LibraryMap.applyFilters(trackMap: Map<Long, List<Long>>): LibraryMap {
        val downloadedOnly = preferences.downloadedOnly().get()
        val filterDownloaded = libraryPreferences.filterDownloaded().get()
        val filterUnread = libraryPreferences.filterUnread().get()
        val filterStarted = libraryPreferences.filterStarted().get()
        val filterBookmarked = libraryPreferences.filterBookmarked().get()
        val filterCompleted = libraryPreferences.filterCompleted().get()

        val loggedInTrackServices = trackManager.services.filter { trackService -> trackService.isLogged }
            .associate { trackService ->
                trackService.id to libraryPreferences.filterTracking(trackService.id.toInt()).get()
            }
        val isNotLoggedInAnyTrack = loggedInTrackServices.isEmpty()

        val excludedTracks = loggedInTrackServices.mapNotNull { if (it.value == State.EXCLUDE.value) it.key else null }
        val includedTracks = loggedInTrackServices.mapNotNull { if (it.value == State.INCLUDE.value) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (LibraryItem) -> Boolean = downloaded@{ item ->
            if (!downloadedOnly && filterDownloaded == State.IGNORE.value) return@downloaded true
            val isDownloaded = when {
                item.libraryManga.manga.isLocal() -> true
                item.downloadCount != -1L -> item.downloadCount > 0
                else -> downloadManager.getDownloadCount(item.libraryManga.manga) > 0
            }

            return@downloaded if (downloadedOnly || filterDownloaded == State.INCLUDE.value) {
                isDownloaded
            } else {
                !isDownloaded
            }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = unread@{ item ->
            if (filterUnread == State.IGNORE.value) return@unread true
            val isUnread = item.libraryManga.unreadCount > 0

            return@unread if (filterUnread == State.INCLUDE.value) {
                isUnread
            } else {
                !isUnread
            }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = started@{ item ->
            if (filterStarted == State.IGNORE.value) return@started true
            val hasStarted = item.libraryManga.hasStarted

            return@started if (filterStarted == State.INCLUDE.value) {
                hasStarted
            } else {
                !hasStarted
            }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = bookmarked@{ item ->
            if (filterBookmarked == State.IGNORE.value) return@bookmarked true

            val hasBookmarks = item.libraryManga.hasBookmarks

            return@bookmarked if (filterBookmarked == State.INCLUDE.value) {
                hasBookmarks
            } else {
                !hasBookmarks
            }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = completed@{ item ->
            if (filterCompleted == State.IGNORE.value) return@completed true
            val isCompleted = item.libraryManga.manga.status.toInt() == SManga.COMPLETED

            return@completed if (filterCompleted == State.INCLUDE.value) {
                isCompleted
            } else {
                !isCompleted
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap[item.libraryManga.id].orEmpty()

            val exclude = mangaTracks.filter { it in excludedTracks }
            val include = mangaTracks.filter { it in includedTracks }

            // TODO: Simplify the filter logic
            if (includedTracks.isNotEmpty() && excludedTracks.isNotEmpty()) {
                return@tracking if (exclude.isNotEmpty()) false else include.isNotEmpty()
            }

            if (excludedTracks.isNotEmpty()) return@tracking exclude.isEmpty()

            if (includedTracks.isNotEmpty()) return@tracking include.isNotEmpty()

            return@tracking false
        }

        val filterFn: (LibraryItem) -> Boolean = filter@{ item ->
            return@filter !(
                !filterFnDownloaded(item) ||
                    !filterFnUnread(item) ||
                    !filterFnStarted(item) ||
                    !filterFnBookmarked(item) ||
                    !filterFnCompleted(item) ||
                    !filterFnTracking(item)
                )
        }

        return this.mapValues { entry -> entry.value.filter(filterFn) }
    }

    /**
     * Applies library sorting to the given map of manga.
     */
    private fun LibraryMap.applySort(categories: List<Category>): LibraryMap {
        val sortModes = categories.associate { it.id to it.sort }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            collator.compare(i1.libraryManga.manga.title.lowercase(locale), i2.libraryManga.manga.title.lowercase(locale))
        }

        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val sort = sortModes[i1.libraryManga.category]!!
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                LibrarySort.Type.LastRead -> {
                    i1.libraryManga.lastRead.compareTo(i2.libraryManga.lastRead)
                }
                LibrarySort.Type.LastUpdate -> {
                    i1.libraryManga.manga.lastUpdate.compareTo(i2.libraryManga.manga.lastUpdate)
                }
                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    i1.libraryManga.unreadCount == i2.libraryManga.unreadCount -> 0
                    i1.libraryManga.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                    i2.libraryManga.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                    else -> i1.libraryManga.unreadCount.compareTo(i2.libraryManga.unreadCount)
                }
                LibrarySort.Type.TotalChapters -> {
                    i1.libraryManga.totalChapters.compareTo(i2.libraryManga.totalChapters)
                }
                LibrarySort.Type.LatestChapter -> {
                    i1.libraryManga.latestUpload.compareTo(i2.libraryManga.latestUpload)
                }
                LibrarySort.Type.ChapterFetchDate -> {
                    i1.libraryManga.chapterFetchedAt.compareTo(i2.libraryManga.chapterFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    i1.libraryManga.manga.dateAdded.compareTo(i2.libraryManga.manga.dateAdded)
                }
                else -> throw IllegalStateException("Invalid SortModeSetting: ${sort.type}")
            }
        }

        return this.mapValues { entry ->
            val comparator = if (sortModes[entry.key]!!.isAscending) {
                Comparator(sortFn)
            } else {
                Collections.reverseOrder(sortFn)
            }

            entry.value.sortedWith(comparator.thenComparator(sortAlphabetically))
        }
    }

    /**
     * Get the categories and all its manga from the database.
     *
     * @return an observable of the categories and its manga.
     */
    private fun getLibraryFlow(): Flow<Library> {
        val libraryMangasFlow = combine(
            getLibraryManga.subscribe(),
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.filterDownloaded().changes(),
            preferences.downloadedOnly().changes(),
            downloadCache.changes,
        ) { libraryMangaList, downloadBadgePref, filterDownloadedPref, downloadedOnly, _ ->
            libraryMangaList
                .map { libraryManga ->
                    val needsDownloadCounts = downloadBadgePref ||
                        filterDownloadedPref != State.IGNORE.value ||
                        downloadedOnly

                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(libraryManga).apply {
                        downloadCount = if (needsDownloadCounts) {
                            downloadManager.getDownloadCount(libraryManga.manga).toLong()
                        } else {
                            0
                        }
                        unreadCount = libraryManga.unreadCount
                        isLocal = libraryManga.manga.isLocal()
                        sourceLanguage = sourceManager.getOrStub(libraryManga.manga.source).lang
                    }
                }
                .groupBy { it.libraryManga.category }
        }

        return combine(getCategories.subscribe(), libraryMangasFlow) { categories, libraryManga ->
            val displayCategories = if (libraryManga.isNotEmpty() && libraryManga.containsKey(0).not()) {
                categories.filterNot { it.isSystemCategory }
            } else {
                categories
            }

            state.categories = displayCategories
            Library(categories, libraryManga)
        }
    }

    /**
     * Requests the library to be filtered.
     */
    suspend fun requestFilterUpdate() = withIOContext {
        _filterChanges.send(Unit)
    }

    /**
     * Called when a manga is opened.
     */
    fun onOpenManga() {
        // Avoid further db updates for the library when it's not needed
        librarySubscription?.cancel()
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * Queues the amount specified of unread chapters from the list of mangas given.
     *
     * @param mangas the list of manga.
     * @param amount the amount to queue or null to queue all
     */
    fun downloadUnreadChapters(mangas: List<Manga>, amount: Int?) {
        presenterScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getNextUnreadChapters.await(manga.id)
                    .filterNot { chapter ->
                        downloadManager.queue.any { chapter.id == it.chapter.id } ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                manga.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters.map { it.toDbChapter() })
            }
        }
    }

    /**
     * Marks mangas' chapters read status.
     *
     * @param mangas the list of manga.
     */
    fun markReadStatus(mangas: List<Manga>, read: Boolean) {
        presenterScope.launchNonCancellable {
            mangas.forEach { manga ->
                setReadStatus.await(
                    manga = manga,
                    read = read,
                )
            }
        }
    }

    /**
     * Remove the selected manga.
     *
     * @param mangaList the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangaList: List<DbManga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        presenterScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id!!,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga.toDomainManga()!!, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        presenterScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    @Composable
    fun getMangaCountForCategory(categoryId: Long): androidx.compose.runtime.State<Int?> {
        return produceState<Int?>(initialValue = null, loadedManga) {
            value = loadedManga[categoryId]?.size
        }
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns() else libraryPreferences.portraitColumns()).asState()
    }

    // TODO: This is good but should we separate title from count or get categories with count from db
    @Composable
    fun getToolbarTitle(): androidx.compose.runtime.State<LibraryToolbarTitle> {
        val category = categories.getOrNull(activeCategory)

        val defaultTitle = stringResource(R.string.label_library)
        val categoryName = category?.visualName ?: defaultTitle

        val default = remember { LibraryToolbarTitle(defaultTitle) }

        return produceState(initialValue = default, category, loadedManga, mangaCountVisibility, tabVisibility) {
            val title = if (tabVisibility.not()) categoryName else defaultTitle
            val count = when {
                category == null || mangaCountVisibility.not() -> null
                tabVisibility.not() -> loadedManga[category.id]?.size
                else -> loadedManga.values.flatten().distinctBy { it.libraryManga.manga.id }.size
            }

            value = when (category) {
                null -> default
                else -> LibraryToolbarTitle(title, count)
            }
        }
    }

    @Composable
    fun getMangaForCategory(page: Int): List<LibraryItem> {
        val unfiltered = remember(categories, loadedManga, page) {
            val categoryId = categories.getOrNull(page)?.id ?: -1
            loadedManga[categoryId] ?: emptyList()
        }
        return remember(unfiltered, searchQuery) {
            val query = searchQuery
            if (query.isNullOrBlank().not()) {
                unfiltered.filter {
                    it.filter(query!!)
                }
            } else {
                unfiltered
            }
        }
    }

    fun clearSelection() {
        state.selection = emptyList()
    }

    fun toggleSelection(manga: LibraryManga) {
        state.selection = selection.toMutableList().apply {
            if (fastAny { it.id == manga.id }) {
                removeAll { it.id == manga.id }
            } else {
                add(manga)
            }
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(manga: LibraryManga) {
        state.selection = selection.toMutableList().apply {
            val lastSelected = lastOrNull()
            if (lastSelected?.category != manga.category) {
                add(manga)
                return@apply
            }
            val items = loadedManga[manga.category].orEmpty().fastMap { it.libraryManga }
            val lastMangaIndex = items.indexOf(lastSelected)
            val curMangaIndex = items.indexOf(manga)
            val selectedIds = fastMap { it.id }
            val newSelections = when (lastMangaIndex >= curMangaIndex + 1) {
                true -> items.subList(curMangaIndex, lastMangaIndex)
                false -> items.subList(lastMangaIndex, curMangaIndex + 1)
            }.filterNot { it.id in selectedIds }
            addAll(newSelections)
        }
    }

    fun selectAll(index: Int) {
        state.selection = state.selection.toMutableList().apply {
            val categoryId = categories[index].id
            val items = loadedManga[categoryId].orEmpty().fastMap { it.libraryManga }
            val selectedIds = fastMap { it.id }
            val newSelections = items.filterNot { it.id in selectedIds }
            addAll(newSelections)
        }
    }

    fun invertSelection(index: Int) {
        state.selection = selection.toMutableList().apply {
            val categoryId = categories[index].id
            val items = loadedManga[categoryId].orEmpty().fastMap { it.libraryManga }
            val selectedIds = fastMap { it.id }
            val (toRemove, toAdd) = items.partition { it.id in selectedIds }
            val toRemoveIds = toRemove.fastMap { it.id }
            removeAll { it.id in toRemoveIds }
            addAll(toAdd)
        }
    }

    sealed class Dialog {
        data class ChangeCategory(val manga: List<Manga>, val initialSelection: List<CheckboxState<Category>>) : Dialog()
        data class DeleteManga(val manga: List<Manga>) : Dialog()
        data class DownloadCustomAmount(val manga: List<Manga>, val max: Int) : Dialog()
    }
}
