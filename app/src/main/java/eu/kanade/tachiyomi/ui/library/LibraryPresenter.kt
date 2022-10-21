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
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.core.util.asFlow
import eu.kanade.core.util.asObservable
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.library.model.LibrarySort
import eu.kanade.domain.library.model.sort
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.track.interactor.GetTracks
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
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
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
    private val getTracks: GetTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
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

    /**
     * Relay used to apply the UI filters to the last emission of the library.
     */
    private val filterTriggerRelay = BehaviorRelay.create(Unit)

    /**
     * Relay used to apply the selected sorting method to the last emission of the library.
     */
    private val sortTriggerRelay = BehaviorRelay.create(Unit)

    private var librarySubscription: Job? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        subscribeLibrary()
    }

    /**
     * Subscribes to library if needed.
     */
    fun subscribeLibrary() {
        /**
         * TODO: Move this to a coroutine world
         * - Move filter and sort to getMangaForCategory and only filter and sort the current display category instead of whole library as some has 5000+ items in the library
         * - Create new db view and new query to just fetch the current category save as needed to instance variable
         * - Fetch badges to maps and retrieve as needed instead of fetching all of them at once
         */
        if (librarySubscription == null || librarySubscription!!.isCancelled) {
            librarySubscription = presenterScope.launchIO {
                getLibraryFlow().asObservable()
                    .combineLatest(getFilterObservable()) { lib, tracks ->
                        lib.copy(mangaMap = applyFilters(lib.mangaMap, tracks))
                    }
                    .combineLatest(sortTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                        lib.copy(mangaMap = applySort(lib.categories, lib.mangaMap))
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .asFlow()
                    .collectLatest {
                        state.isLoading = false
                        loadedManga = it.mangaMap
                    }
            }
        }
    }

    /**
     * Applies library filters to the given map of manga.
     *
     * @param map the map to filter.
     */
    private fun applyFilters(map: LibraryMap, trackMap: Map<Long, Map<Long, Boolean>>): LibraryMap {
        val downloadedOnly = preferences.downloadedOnly().get()
        val filterDownloaded = libraryPreferences.filterDownloaded().get()
        val filterUnread = libraryPreferences.filterUnread().get()
        val filterStarted = libraryPreferences.filterStarted().get()
        val filterBookmarked = libraryPreferences.filterBookmarked().get()
        val filterCompleted = libraryPreferences.filterCompleted().get()
        val loggedInServices = trackManager.services.filter { trackService -> trackService.isLogged }
            .associate { trackService ->
                Pair(trackService.id, libraryPreferences.filterTracking(trackService.id.toInt()).get())
            }
        val isNotAnyLoggedIn = !loggedInServices.values.any()

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
            if (isNotAnyLoggedIn) return@tracking true

            val trackedManga = trackMap[item.libraryManga.manga.id]

            val containsExclude = loggedInServices.filterValues { it == State.EXCLUDE.value }
            val containsInclude = loggedInServices.filterValues { it == State.INCLUDE.value }

            if (!containsExclude.any() && !containsInclude.any()) return@tracking true

            val exclude = trackedManga?.filterKeys { containsExclude.containsKey(it) }?.values ?: emptyList()
            val include = trackedManga?.filterKeys { containsInclude.containsKey(it) }?.values ?: emptyList()

            if (containsInclude.any() && containsExclude.any()) {
                return@tracking if (exclude.isNotEmpty()) !exclude.any() else include.any()
            }

            if (containsExclude.any()) return@tracking !exclude.any()

            if (containsInclude.any()) return@tracking include.any()

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

        return map.mapValues { entry -> entry.value.filter(filterFn) }
    }

    /**
     * Applies library sorting to the given map of manga.
     *
     * @param map the map to sort.
     */
    private fun applySort(categories: List<Category>, map: LibraryMap): LibraryMap {
        val sortModes = categories.associate { it.id to it.sort }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val sort = sortModes[i1.libraryManga.category]!!
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    collator.compare(i1.libraryManga.manga.title.lowercase(locale), i2.libraryManga.manga.title.lowercase(locale))
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

        return map.mapValues { entry ->
            val comparator = if (sortModes[entry.key]!!.isAscending) {
                Comparator(sortFn)
            } else {
                Collections.reverseOrder(sortFn)
            }

            entry.value.sortedWith(comparator)
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
            downloadCache.changes,
        ) { libraryMangaList, downloadBadgePref, _ ->
            libraryMangaList
                .map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(libraryManga).apply {
                        downloadCount = if (downloadBadgePref) {
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
     * Get the tracked manga from the database and checks if the filter gets changed
     *
     * @return an observable of tracked manga.
     */
    private fun getFilterObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        return filterTriggerRelay.observeOn(Schedulers.io())
            .combineLatest(getTracksFlow().asObservable().observeOn(Schedulers.io())) { _, tracks -> tracks }
    }

    /**
     * Get the tracked manga from the database
     *
     * @return an observable of tracked manga.
     */
    private fun getTracksFlow(): Flow<Map<Long, Map<Long, Boolean>>> {
        // TODO: Move this to domain/data layer
        return getTracks.subscribe()
            .map { tracks ->
                tracks
                    .groupBy { it.mangaId }
                    .mapValues { tracksForMangaId ->
                        // Check if any of the trackers is logged in for the current manga id
                        tracksForMangaId.value.associate {
                            Pair(it.syncId, trackManager.getService(it.syncId)?.isLogged ?: false)
                        }
                    }
            }
    }

    /**
     * Requests the library to be filtered.
     */
    fun requestFilterUpdate() {
        filterTriggerRelay.call(Unit)
    }

    /**
     * Requests the library to be sorted.
     */
    fun requestSortUpdate() {
        sortTriggerRelay.call(Unit)
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
        return mangas.toSet()
            .map { getCategories.await(it.id) }
            .reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.toSet().map { getCategories.await(it.id) }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
        return mangaCategories.flatten().distinct().subtract(common).toMutableList()
    }

    /**
     * Queues all unread chapters from the given list of manga.
     *
     * @param mangas the list of manga.
     */
    fun downloadUnreadChapters(mangas: List<Manga>) {
        presenterScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getChapterByMangaId.await(manga.id)
                    .filter { !it.read }
                    .map { it.toDbChapter() }

                downloadManager.downloadChapters(manga, chapters)
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
            mangaList.map { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories)
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
        val unfiltered = remember(categories, loadedManga) {
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
            if (lastSelected == null || lastSelected.category != manga.category) {
                add(manga)
                return@apply
            }
            val items = (loadedManga[manga.category] ?: emptyList()).map { it.libraryManga }
            val lastMangaIndex = items.indexOf(lastSelected)
            val curMangaIndex = items.indexOf(manga)
            val selectedIds = map { it.id }
            val newSelections = when (lastMangaIndex >= curMangaIndex + 1) {
                true -> items.subList(curMangaIndex, lastMangaIndex)
                false -> items.subList(lastMangaIndex, curMangaIndex + 1)
            }.filterNot { it.id in selectedIds }
            addAll(newSelections)
        }
    }

    fun selectAll(index: Int) {
        val category = categories[index]
        val items = loadedManga[category.id] ?: emptyList()
        state.selection = state.selection.toMutableList().apply {
            addAll(items.filterNot { it.libraryManga in selection }.map { it.libraryManga })
        }
    }

    fun invertSelection(index: Int) {
        val category = categories[index]
        val items = (loadedManga[category.id] ?: emptyList()).map { it.libraryManga }
        state.selection = items.filterNot { it in selection }
    }

    private fun <T, U, R> Observable<T>.combineLatest(o2: Observable<U>, combineFn: (T, U) -> R): Observable<R> {
        return Observable.combineLatest(this, o2, combineFn)
    }

    sealed class Dialog {
        data class ChangeCategory(val manga: List<Manga>, val initialSelection: List<CheckboxState<Category>>) : Dialog()
        data class DeleteManga(val manga: List<Manga>) : Dialog()
    }
}
