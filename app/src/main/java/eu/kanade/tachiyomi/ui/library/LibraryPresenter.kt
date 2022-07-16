package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.core.util.asObservable
import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.util.lang.combineLatest
import eu.kanade.tachiyomi.util.lang.isNullOrUnsubscribed
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import rx.Observable
import rx.Subscription
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

/**
 * Presenter of [LibraryController].
 */
class LibraryPresenter(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
) : BasePresenter<LibraryController>() {

    private val context = preferences.context

    /**
     * Categories of the library.
     */
    var categories: List<Category> = mutableStateListOf()
        private set

    var loadedManga = mutableStateMapOf<Long, List<LibraryItem>>()
        private set

    val loadedMangaFlow = MutableStateFlow(loadedManga)

    var searchQuery by mutableStateOf(query)

    val selection: MutableList<LibraryManga> = mutableStateListOf()

    val isPerCategory by preferences.categorizedDisplaySettings().asState()

    var columns by mutableStateOf(0)

    var currentDisplayMode by preferences.libraryDisplayMode().asState()

    /**
     * Relay used to apply the UI filters to the last emission of the library.
     */
    private val filterTriggerRelay = BehaviorRelay.create(Unit)

    /**
     * Relay used to apply the UI update to the last emission of the library.
     */
    private val badgeTriggerRelay = BehaviorRelay.create(Unit)

    /**
     * Relay used to apply the selected sorting method to the last emission of the library.
     */
    private val sortTriggerRelay = BehaviorRelay.create(Unit)

    private var librarySubscription: Subscription? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        subscribeLibrary()
    }

    /**
     * Subscribes to library if needed.
     */
    fun subscribeLibrary() {
        // TODO: Move this to a coroutine world
        if (librarySubscription.isNullOrUnsubscribed()) {
            librarySubscription = getLibraryObservable()
                .combineLatest(badgeTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                    lib.apply { setBadges(mangaMap) }
                }
                .combineLatest(getFilterObservable()) { lib, tracks ->
                    lib.copy(mangaMap = applyFilters(lib.mangaMap, tracks))
                }
                .combineLatest(sortTriggerRelay.observeOn(Schedulers.io())) { lib, _ ->
                    lib.copy(mangaMap = applySort(lib.categories, lib.mangaMap))
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache({ view, (categories, mangaMap) ->
                    view.onNextLibraryUpdate(categories, mangaMap)
                },)
        }
    }

    /**
     * Applies library filters to the given map of manga.
     *
     * @param map the map to filter.
     */
    private fun applyFilters(map: LibraryMap, trackMap: Map<Long, Map<Long, Boolean>>): LibraryMap {
        val downloadedOnly = preferences.downloadedOnly().get()
        val filterDownloaded = preferences.filterDownloaded().get()
        val filterUnread = preferences.filterUnread().get()
        val filterStarted = preferences.filterStarted().get()
        val filterCompleted = preferences.filterCompleted().get()
        val loggedInServices = trackManager.services.filter { trackService -> trackService.isLogged }
            .associate { trackService ->
                Pair(trackService.id, preferences.filterTracking(trackService.id.toInt()).get())
            }
        val isNotAnyLoggedIn = !loggedInServices.values.any()

        val filterFnDownloaded: (LibraryItem) -> Boolean = downloaded@{ item ->
            if (!downloadedOnly && filterDownloaded == State.IGNORE.value) return@downloaded true
            val isDownloaded = when {
                item.manga.toDomainManga()!!.isLocal() -> true
                item.downloadCount != -1 -> item.downloadCount > 0
                else -> downloadManager.getDownloadCount(item.manga.toDomainManga()!!) > 0
            }

            return@downloaded if (downloadedOnly || filterDownloaded == State.INCLUDE.value) isDownloaded
            else !isDownloaded
        }

        val filterFnUnread: (LibraryItem) -> Boolean = unread@{ item ->
            if (filterUnread == State.IGNORE.value) return@unread true
            val isUnread = item.manga.unreadCount != 0

            return@unread if (filterUnread == State.INCLUDE.value) isUnread
            else !isUnread
        }

        val filterFnStarted: (LibraryItem) -> Boolean = started@{ item ->
            if (filterStarted == State.IGNORE.value) return@started true
            val hasStarted = item.manga.hasStarted

            return@started if (filterStarted == State.INCLUDE.value) hasStarted
            else !hasStarted
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = completed@{ item ->
            if (filterCompleted == State.IGNORE.value) return@completed true
            val isCompleted = item.manga.status == SManga.COMPLETED

            return@completed if (filterCompleted == State.INCLUDE.value) isCompleted
            else !isCompleted
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotAnyLoggedIn) return@tracking true

            val trackedManga = trackMap[item.manga.id ?: -1]

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
                    !filterFnCompleted(item) ||
                    !filterFnTracking(item)
                )
        }

        return map.mapValues { entry -> entry.value.filter(filterFn) }
    }

    /**
     * Sets downloaded chapter count to each manga.
     *
     * @param map the map of manga.
     */
    private fun setBadges(map: LibraryMap) {
        val showDownloadBadges = preferences.downloadBadge().get()
        val showUnreadBadges = preferences.unreadBadge().get()
        val showLocalBadges = preferences.localBadge().get()
        val showLanguageBadges = preferences.languageBadge().get()

        for ((_, itemList) in map) {
            for (item in itemList) {
                item.downloadCount = if (showDownloadBadges) {
                    downloadManager.getDownloadCount(item.manga.toDomainManga()!!)
                } else {
                    // Unset download count if not enabled
                    -1
                }

                item.unreadCount = if (showUnreadBadges) {
                    item.manga.unreadCount
                } else {
                    // Unset unread count if not enabled
                    -1
                }

                item.isLocal = if (showLocalBadges) {
                    item.manga.toDomainManga()!!.isLocal()
                } else {
                    // Hide / Unset local badge if not enabled
                    false
                }

                item.sourceLanguage = if (showLanguageBadges) {
                    sourceManager.getOrStub(item.manga.source).lang.uppercase()
                } else {
                    // Unset source language if not enabled
                    ""
                }
            }
        }
    }

    /**
     * Applies library sorting to the given map of manga.
     *
     * @param map the map to sort.
     */
    private fun applySort(categories: List<Category>, map: LibraryMap): LibraryMap {
        val lastReadManga by lazy {
            var counter = 0
            // TODO: Make [applySort] a suspended function
            runBlocking {
                handler.awaitList {
                    mangasQueries.getLastRead()
                }.associate { it._id to counter++ }
            }
        }
        val latestChapterManga by lazy {
            var counter = 0
            // TODO: Make [applySort] a suspended function
            runBlocking {
                handler.awaitList {
                    mangasQueries.getLatestByChapterUploadDate()
                }.associate { it._id to counter++ }
            }
        }
        val chapterFetchDateManga by lazy {
            var counter = 0
            // TODO: Make [applySort] a suspended function
            runBlocking {
                handler.awaitList {
                    mangasQueries.getLatestByChapterFetchDate()
                }.associate { it._id to counter++ }
            }
        }

        val sortingModes = categories.associate { category ->
            category.id to SortModeSetting.get(preferences, category)
        }

        val sortDirections = categories.associate { category ->
            category.id to SortDirectionSetting.get(preferences, category)
        }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val sortingMode = sortingModes[i1.manga.category.toLong()]!!
            val sortAscending = sortDirections[i1.manga.category.toLong()]!! == SortDirectionSetting.ASCENDING
            when (sortingMode) {
                SortModeSetting.ALPHABETICAL -> {
                    collator.compare(i1.manga.title.lowercase(locale), i2.manga.title.lowercase(locale))
                }
                SortModeSetting.LAST_READ -> {
                    val manga1LastRead = lastReadManga[i1.manga.id!!] ?: 0
                    val manga2LastRead = lastReadManga[i2.manga.id!!] ?: 0
                    manga1LastRead.compareTo(manga2LastRead)
                }
                SortModeSetting.LAST_MANGA_UPDATE -> {
                    i1.manga.last_update.compareTo(i2.manga.last_update)
                }
                SortModeSetting.UNREAD_COUNT -> when {
                    // Ensure unread content comes first
                    i1.manga.unreadCount == i2.manga.unreadCount -> 0
                    i1.manga.unreadCount == 0 -> if (sortAscending) 1 else -1
                    i2.manga.unreadCount == 0 -> if (sortAscending) -1 else 1
                    else -> i1.manga.unreadCount.compareTo(i2.manga.unreadCount)
                }
                SortModeSetting.TOTAL_CHAPTERS -> {
                    i1.manga.totalChapters.compareTo(i2.manga.totalChapters)
                }
                SortModeSetting.LATEST_CHAPTER -> {
                    val manga1latestChapter = latestChapterManga[i1.manga.id!!]
                        ?: latestChapterManga.size
                    val manga2latestChapter = latestChapterManga[i2.manga.id!!]
                        ?: latestChapterManga.size
                    manga1latestChapter.compareTo(manga2latestChapter)
                }
                SortModeSetting.CHAPTER_FETCH_DATE -> {
                    val manga1chapterFetchDate = chapterFetchDateManga[i1.manga.id!!] ?: 0
                    val manga2chapterFetchDate = chapterFetchDateManga[i2.manga.id!!] ?: 0
                    manga1chapterFetchDate.compareTo(manga2chapterFetchDate)
                }
                SortModeSetting.DATE_ADDED -> {
                    i1.manga.date_added.compareTo(i2.manga.date_added)
                }
                else -> throw IllegalStateException("Invalid SortModeSetting: $sortingMode")
            }
        }

        return map.mapValues { entry ->
            val sortAscending = sortDirections[entry.key.toLong()]!! == SortDirectionSetting.ASCENDING

            val comparator = if (sortAscending) {
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
    private fun getLibraryObservable(): Observable<Library> {
        return Observable.combineLatest(getCategoriesObservable(), getLibraryMangasObservable()) { dbCategories, libraryManga ->
            val categories = if (libraryManga.containsKey(0)) {
                arrayListOf(Category.default(context)) + dbCategories
            } else {
                dbCategories
            }

            libraryManga.forEach { (categoryId, libraryManga) ->
                val category = categories.first { category -> category.id == categoryId.toLong() }
                libraryManga.forEach { libraryItem ->
                    libraryItem.displayMode = category.displayMode
                }
            }

            this.categories = categories
            Library(categories, libraryManga)
        }
    }

    /**
     * Get the categories from the database.
     *
     * @return an observable of the categories.
     */
    private fun getCategoriesObservable(): Observable<List<Category>> {
        return getCategories.subscribe().asObservable()
    }

    /**
     * Get the manga grouped by categories.
     *
     * @return an observable containing a map with the category id as key and a list of manga as the
     * value.
     */
    private fun getLibraryMangasObservable(): Observable<LibraryMap> {
        return getLibraryManga.subscribe().asObservable()
            .map { list ->
                list.map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(libraryManga)
                }.groupBy { it.manga.category.toLong() }
            }
    }

    /**
     * Get the tracked manga from the database and checks if the filter gets changed
     *
     * @return an observable of tracked manga.
     */
    private fun getFilterObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        return getTracksObservable().combineLatest(filterTriggerRelay.observeOn(Schedulers.io())) { tracks, _ -> tracks }
    }

    /**
     * Get the tracked manga from the database
     *
     * @return an observable of tracked manga.
     */
    private fun getTracksObservable(): Observable<Map<Long, Map<Long, Boolean>>> {
        // TODO: Move this to domain/data layer
        return getTracks.subscribe()
            .asObservable().map { tracks ->
                tracks
                    .groupBy { it.mangaId }
                    .mapValues { tracksForMangaId ->
                        // Check if any of the trackers is logged in for the current manga id
                        tracksForMangaId.value.associate {
                            Pair(it.syncId, trackManager.getService(it.syncId)?.isLogged ?: false)
                        }
                    }
            }
            .observeOn(Schedulers.io())
    }

    /**
     * Requests the library to be filtered.
     */
    fun requestFilterUpdate() {
        filterTriggerRelay.call(Unit)
    }

    /**
     * Requests the library to have download badges added.
     */
    fun requestBadgesUpdate() {
        badgeTriggerRelay.call(Unit)
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
        librarySubscription?.let { remove(it) }
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
        mangas.forEach { manga ->
            launchIO {
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
        mangas.forEach { manga ->
            launchIO {
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
        launchIO {
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
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        presenterScope.launchIO {
            mangaList.map { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .subtract(removeCategories)
                    .plus(addCategories)
                    .map { it.id }
                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    @Composable
    fun getMangaForCategory(categoryId: Long): androidx.compose.runtime.State<List<LibraryItem>> {
        val unfiltered = loadedManga[categoryId] ?: emptyList()

        return derivedStateOf {
            val query = searchQuery
            if (query.isNotBlank()) {
                unfiltered.filter {
                    it.filter(query)
                }
            } else {
                unfiltered
            }
        }
    }

    @Composable
    fun getDisplayMode(index: Int): DisplayModeSetting {
        val category = categories[index]
        return remember {
            if (isPerCategory.not() || category.id == 0L) {
                currentDisplayMode
            } else {
                DisplayModeSetting.fromFlag(category.displayMode)
            }
        }
    }

    fun hasSelection(): Boolean {
        return selection.isNotEmpty()
    }

    fun clearSelection() {
        selection.clear()
    }

    fun toggleSelection(manga: LibraryManga) {
        if (selection.fastAny { it.id == manga.id }) {
            selection.remove(manga)
        } else {
            selection.add(manga)
        }
        view?.invalidateActionMode()
        view?.createActionModeIfNeeded()
    }

    fun selectAll(index: Int) {
        val category = categories[index]
        val items = loadedManga[category.id] ?: emptyList()
        selection.addAll(items.filterNot { it.manga in selection }.map { it.manga })
        view?.createActionModeIfNeeded()
        view?.invalidateActionMode()
    }

    fun invertSelection(index: Int) {
        val category = categories[index]
        val items = (loadedManga[category.id] ?: emptyList()).map { it.manga }
        val invert = items.filterNot { it in selection }
        selection.removeAll(items)
        selection.addAll(invert)
        view?.createActionModeIfNeeded()
        view?.invalidateActionMode()
    }
}
