package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationService
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.RemoveChapters
import tachiyomi.domain.chapter.interactor.SearchChapterNames
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

class LibraryScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val removeChapters: RemoveChapters = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val searchChapterNames: SearchChapterNames = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val type: LibraryType = LibraryType.All,
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    enum class LibraryType {
        All,
        Manga,
        Novel,
    }

    init {
        mutableState.update { state ->
            state.copy(activeCategoryIndex = libraryPreferences.lastUsedCategory().get())
        }
        screenModelScope.launchIO {
            // Subscribe to categories filtered by content type based on library type
            val categoriesFlow = when (type) {
                LibraryType.All -> getCategories.subscribe()
                LibraryType.Manga -> getCategories.subscribeByContentType(Category.CONTENT_TYPE_MANGA)
                LibraryType.Novel -> getCategories.subscribeByContentType(Category.CONTENT_TYPE_NOVEL)
            }

            // Flow that emits manga IDs with matching chapter names when search is active
            val searchQueryFlow = state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS)
            
            val chapterMatchIdsFlow = combine(
                searchQueryFlow,
                libraryPreferences.searchChapterNames().changes(),
            ) { query, searchChapters ->
                if (query.isNullOrEmpty() || !searchChapters) {
                    emptySet()
                } else {
                    // For "chapter:" prefix, extract the actual query
                    val actualQuery = if (query.startsWith("chapter:", true)) {
                        query.substringAfter("chapter:").trim()
                    } else {
                        query
                    }
                    if (actualQuery.isNotEmpty()) {
                        withIOContext { searchChapterNames.await(actualQuery).toSet() }
                    } else {
                        emptySet()
                    }
                }
            }

            // Combine search query with chapter match IDs
            val searchWithChapterMatchesFlow = combine(
                searchQueryFlow,
                chapterMatchIdsFlow,
            ) { query, chapterMatchIds ->
                Pair(query, chapterMatchIds)
            }

            combine(
                searchWithChapterMatchesFlow,
                categoriesFlow,
                getFavoritesFlow(),
                combine(getTracksPerManga.subscribe(), getTrackingFiltersFlow(), ::Pair),
                getLibraryItemPreferencesFlow(),
                getLibraryManga.isLoading(),
            ) { flows: Array<*> ->
                val (searchQuery, chapterMatchIds) = flows[0] as Pair<*, *>
                @Suppress("UNCHECKED_CAST")
                val categories = flows[1] as List<Category>
                @Suppress("UNCHECKED_CAST")
                val favorites = flows[2] as List<LibraryItem>
                val (tracksMap, trackingFilters) = flows[3] as Pair<*, *>
                val itemPreferences = flows[4]
                val isLoading = flows[5] as Boolean
                
                val showSystemCategory = favorites.any { it.libraryManga.categories.contains(0) }
                @Suppress("UNCHECKED_CAST")
                val filteredFavorites = favorites
                    .applyFilters(tracksMap as Map<Long, List<Track>>, trackingFilters as Map<Long, TriState>, itemPreferences as ItemPreferences)
                    .let { if (searchQuery == null) it else it.filter { m -> m.matches(searchQuery as String, chapterMatchIds as Set<Long>) } }

                LibraryData(
                    isInitialized = !isLoading,
                    showSystemCategory = showSystemCategory,
                    categories = categories,
                    favorites = filteredFavorites,
                    tracksMap = tracksMap as Map<Long, List<Track>>,
                    loggedInTrackerIds = (trackingFilters as Map<Long, TriState>).keys,
                )
            }
                .distinctUntilChanged()
                .collectLatest { libraryData ->
                    mutableState.update { state ->
                        state.copy(libraryData = libraryData)
                    }
                }
        }

        screenModelScope.launchIO {
            state
                .dropWhile { !it.libraryData.isInitialized }
                .map { it.libraryData }
                .distinctUntilChanged()
                .map { data ->
                    data.favorites
                        .applyGrouping(data.categories, data.showSystemCategory)
                        .applySort(data.favoritesById, data.tracksMap, data.loggedInTrackerIds)
                }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            groupedFavorites = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueReadingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showMangaCount, showMangaContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showMangaCount = showMangaCount,
                        showMangaContinueButton = showMangaContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            getLibraryItemPreferencesFlow(),
            getTrackingFiltersFlow(),
        ) { prefs, trackFilters ->
            listOf(
                prefs.filterDownloaded,
                prefs.filterUnread,
                prefs.filterStarted,
                prefs.filterBookmarked,
                prefs.filterCompleted,
                prefs.filterIntervalCustom,
                prefs.filterNoTags,
                *trackFilters.values.toTypedArray(),
            )
                .any { it != TriState.DISABLED } ||
                prefs.includedTags.isNotEmpty() ||
                prefs.excludedTags.isNotEmpty()
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)
    }

    private fun List<LibraryItem>.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
        preferences: ItemPreferences,
    ): List<LibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
        val filterUnread = preferences.filterUnread
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted
        val filterIntervalCustom = preferences.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryManga.manga.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryManga.manga) > 0
            }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFnExtensions: (LibraryItem) -> Boolean = { item ->
            // If extension is in excluded set, hide it
            item.libraryManga.manga.source.toString() !in preferences.excludedExtensions
        }

        val filterFnTags: (LibraryItem) -> Boolean = tags@{ item ->
            val mangaTags = item.libraryManga.manga.genre
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            // Handle "No Tags" filter
            val noTagsFilter = preferences.filterNoTags
            if (noTagsFilter != TriState.DISABLED) {
                val hasNoTags = mangaTags.isEmpty()
                when (noTagsFilter) {
                    TriState.ENABLED_IS -> if (!hasNoTags) return@tags false
                    TriState.ENABLED_NOT -> if (hasNoTags) return@tags false
                    else -> {}
                }
            }

            val includedTags = preferences.includedTags
            val excludedTags = preferences.excludedTags

            // If no tag filters are set, pass through
            if (includedTags.isEmpty() && excludedTags.isEmpty()) return@tags true

            // Check excluded tags first - if any excluded tag is present, filter out
            if (excludedTags.isNotEmpty() && mangaTags.any { tag -> tag in excludedTags }) {
                return@tags false
            }

            // Check included tags - if any included tag is present, include the item
            if (includedTags.isNotEmpty() && mangaTags.none { tag -> tag in includedTags }) {
                return@tags false
            }

            true
        }

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it) &&
                filterFnExtensions(it) &&
                filterFnTags(it)
        }
    }

    private fun List<LibraryItem>.applyGrouping(
        categories: List<Category>,
        showSystemCategory: Boolean,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val groupCache = mutableMapOf</* Category */ Long, MutableList</* LibraryItem */ Long>>()
        forEach { item ->
            item.libraryManga.categories.forEach { categoryId ->
                groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
            }
        }
        return categories.filter { showSystemCategory || !it.isSystemCategory }
            .associateWith { groupCache[it.id]?.toList().orEmpty() }
    }

    private fun Map<Category, List</* LibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, LibraryItem>,
        trackMap: Map<Long, List<Track>>,
        loggedInTrackerIds: Set<Long>,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { manga1, manga2 ->
            val title1 = manga1.libraryManga.manga.title.lowercase()
            val title2 = manga2.libraryManga.manga.title.lowercase()
            title1.compareToWithCollator(title2)
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { manga1, manga2 ->
            when (this.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(manga1, manga2)
                }
                LibrarySort.Type.LastRead -> {
                    manga1.libraryManga.lastRead.compareTo(manga2.libraryManga.lastRead)
                }
                LibrarySort.Type.LastUpdate -> {
                    manga1.libraryManga.manga.lastUpdate.compareTo(manga2.libraryManga.manga.lastUpdate)
                }
                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                    manga1.libraryManga.unreadCount == 0L -> if (this.isAscending) 1 else -1
                    manga2.libraryManga.unreadCount == 0L -> if (this.isAscending) -1 else 1
                    else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
                }
                LibrarySort.Type.TotalChapters -> {
                    manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
                }
                LibrarySort.Type.LatestChapter -> {
                    manga1.libraryManga.latestUpload.compareTo(manga2.libraryManga.latestUpload)
                }
                LibrarySort.Type.ChapterFetchDate -> {
                    manga1.libraryManga.chapterFetchedAt.compareTo(manga2.libraryManga.chapterFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    manga1.libraryManga.manga.dateAdded.compareTo(manga2.libraryManga.manga.dateAdded)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[manga1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[manga2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == LibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed().get()))
            }

            val manga = value.mapNotNull { favoritesById[it] }

            val comparator = key.sort.comparator()
                .let { if (key.sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            manga.sortedWith(comparator).map { it.id }
        }
    }

    private fun isNovel(sourceId: Long): Boolean {
        return sourceManager.get(sourceId)?.isNovelSource() == true
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            combine(
                libraryPreferences.downloadBadge().changes(),
                libraryPreferences.unreadBadge().changes(),
                libraryPreferences.localBadge().changes(),
                libraryPreferences.languageBadge().changes(),
                libraryPreferences.autoUpdateMangaRestrictions().changes(),
                preferences.downloadedOnly().changes(),
            ) { arr -> arr },
            combine(
                libraryPreferences.filterDownloaded().changes(),
                libraryPreferences.filterUnread().changes(),
                libraryPreferences.filterStarted().changes(),
                libraryPreferences.filterBookmarked().changes(),
                libraryPreferences.filterCompleted().changes(),
                libraryPreferences.filterIntervalCustom().changes(),
                libraryPreferences.excludedExtensions().changes(),
            ) { arr -> arr },
            combine(
                libraryPreferences.includedTags().changes(),
                libraryPreferences.excludedTags().changes(),
                libraryPreferences.filterNoTags().changes(),
            ) { arr -> arr },
        ) { first, second, third ->
            ItemPreferences(
                downloadBadge = first[0] as Boolean,
                unreadBadge = first[1] as Boolean,
                localBadge = first[2] as Boolean,
                languageBadge = first[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in (first[4] as Set<*>),
                globalFilterDownloaded = first[5] as Boolean,
                filterDownloaded = second[0] as TriState,
                filterUnread = second[1] as TriState,
                filterStarted = second[2] as TriState,
                filterBookmarked = second[3] as TriState,
                filterCompleted = second[4] as TriState,
                filterIntervalCustom = second[5] as TriState,
                excludedExtensions = @Suppress("UNCHECKED_CAST") (second[6] as Set<String>),
                includedTags = @Suppress("UNCHECKED_CAST") (third[0] as Set<String>),
                excludedTags = @Suppress("UNCHECKED_CAST") (third[1] as Set<String>),
                filterNoTags = third[2] as TriState,
            )
        }
    }

    private fun getFavoritesFlow(): Flow<List<LibraryItem>> {
        return combine(
            // Repository already has debounce(300) and distinctUntilChanged()
            getLibraryManga.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryManga, preferences, _ ->
            libraryManga
                .filter { item ->
                    when (type) {
                        LibraryType.All -> true
                        LibraryType.Manga -> !isNovel(item.manga.source)
                        LibraryType.Novel -> isNovel(item.manga.source)
                    }
                }
                .map { manga ->
                    LibraryItem(
                        libraryManga = manga,
                        downloadCount = if (preferences.downloadBadge) {
                            downloadManager.getDownloadCount(manga.manga).toLong()
                        } else {
                            0
                        },
                        unreadCount = if (preferences.unreadBadge) {
                            manga.unreadCount
                        } else {
                            0
                        },
                        isLocal = if (preferences.localBadge) {
                            manga.manga.isLocal()
                        } else {
                            false
                        },
                        sourceLanguage = if (preferences.languageBadge) {
                            sourceManager.getOrStub(manga.manga.source).lang
                        } else {
                            ""
                        },
                    )
                }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFiltersFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val filterFlows = loggedInTrackers.map { tracker ->
                    libraryPreferences.filterTracking(tracker.id.toInt()).changes().map { tracker.id to it }
                }
                combine(filterFlows) { it.toMap() }
            }
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        return getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true).getNextUnread(manga, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * Queues the amount specified of unread chapters from the list of selected manga
     */
    fun performDownloadAction(action: DownloadAction) {
        val mangas = state.value.selectedManga
        val amount = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> 1
            DownloadAction.NEXT_5_CHAPTERS -> 5
            DownloadAction.NEXT_10_CHAPTERS -> 10
            DownloadAction.NEXT_25_CHAPTERS -> 25
            DownloadAction.UNREAD_CHAPTERS -> null
        }
        clearSelection()
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getNextChapters.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                chapter.url,
                                manga.title,
                                manga.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    /**
     * Queues all downloaded chapters from the selected novels for translation.
     * Only processes chapters that haven't been translated yet.
     */
    fun translateSelectedNovels() {
        val translationService: TranslationService = Injekt.get()
        val mangas = state.value.selectedManga.filter { manga ->
            // Only process novels (not manga)
            val source = sourceManager.get(manga.source)
            source?.isNovelSource() == true
        }
        clearSelection()
        screenModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getChaptersByMangaId.await(manga.id)
                // Queue all chapters for translation (TranslationService will skip already translated ones)
                translationService.enqueueAll(manga, chapters)
            }
        }
    }

    /**
     * Show confirmation dialog for marking mangas read/unread.
     */
    fun showMarkReadConfirmation(read: Boolean) {
        mutableState.update {
            it.copy(dialog = Dialog.MarkReadConfirmation(read))
        }
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val selection = state.value.selectedManga
        screenModelScope.launchNonCancellable {
            selection.forEach { manga ->
                setReadStatus.await(
                    manga = manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            if (deleteFromLibrary) {
                val toDelete = mangas.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangas.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
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
        screenModelScope.launchNonCancellable {
            val mangaIds = mangaList.map { it.id }
            if (addCategories.isNotEmpty()) {
                setMangaCategories.add(mangaIds, addCategories)
            }
            if (removeCategories.isNotEmpty()) {
                setMangaCategories.remove(mangaIds, removeCategories)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns() else libraryPreferences.portraitColumns())
            .asState(screenModelScope)
    }

    fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        val state = state.value
        return state.getItemsForCategoryId(state.activeCategory?.id).randomOrNull()
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    private var lastSelectionCategory: Long? = null

    fun clearSelection() {
        lastSelectionCategory = null
        mutableState.update { it.copy(selection = setOf()) }
    }

    fun toggleSelection(category: Category, manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { set ->
                if (!set.remove(manga.id)) set.add(manga.id)
            }
            lastSelectionCategory = category.id.takeIf { newSelection.isNotEmpty() }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(category: Category, manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelectionCategory != category.id) {
                    list.add(manga.id)
                    return@mutate
                }

                val items = state.getItemsForCategoryId(category.id).fastMap { it.id }
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga.id)

                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
                    curMangaIndex < lastMangaIndex -> curMangaIndex..lastMangaIndex
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                selectionRange.mapNotNull { items[it] }.let(list::addAll)
            }
            lastSelectionCategory = category.id
            state.copy(selection = newSelection)
        }
    }

    fun selectAll() {
        lastSelectionCategory = null
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                state.getItemsForCategoryId(state.activeCategory?.id).map { it.id }.let(list::addAll)
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection() {
        lastSelectionCategory = null
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val itemIds = state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }
                val (toRemove, toAdd) = itemIds.partition { it in list }
                list.removeAll(toRemove)
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Returns the URLs of the selected manga.
     * Uses the source's getMangaUrl method to get the proper URL for each manga.
     */
    fun getSelectedMangaUrls(): List<String> {
        return state.value.selectedManga.mapNotNull { manga ->
            val source = sourceManager.get(manga.source) as? HttpSource
            source?.let {
                try {
                    it.getMangaUrl(manga.toSManga())
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Update selected novels by fetching chapters from source
     */
    fun updateSelectedNovels() {
        val selectedManga = state.value.selectedManga
        if (selectedManga.isEmpty()) return

        screenModelScope.launchIO {
            val results = selectedManga.map { manga ->
                async {
                    try {
                        val source = sourceManager.get(manga.source) as? HttpSource
                        if (source == null) {
                            logcat(LogPriority.WARN) { "No source for ${manga.title}" }
                            return@async false
                        }

                        // Fetch chapters from source
                        val chapters = withIOContext { source.getChapterList(manga.toSManga()) }
                        syncChaptersWithSource.await(chapters, manga, source)
                        logcat(LogPriority.DEBUG) { "Updated ${manga.title}: ${chapters.size} chapters" }
                        true
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to update ${manga.title}" }
                        false
                    }
                }
            }
            val outcomes = results.awaitAll()
            val successCount = outcomes.count { it }
            logcat(LogPriority.INFO) { "Batch update complete: $successCount/${selectedManga.size} succeeded" }
        }
        clearSelection()
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun updateActiveCategoryIndex(index: Int) {
        val newIndex = mutableState.updateAndGet { state ->
            state.copy(activeCategoryIndex = index)
        }
            .coercedActiveCategoryIndex

        libraryPreferences.lastUsedCategory().set(newIndex)
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected manga
            val mangaList = state.value.selectedManga

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.displayedCategories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(state.value.selectedManga)) }
    }

    fun openRemoveChaptersDialog() {
        mutableState.update { it.copy(dialog = Dialog.RemoveChapters(state.value.selectedManga)) }
    }

    fun removeChaptersFromSelectedManga(mangaList: List<Manga>) {
        screenModelScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val chapters = getChaptersByMangaId.await(manga.id)
                removeChapters.await(chapters)
            }
        }
        clearSelection()
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun openMassImportDialog() {
        mutableState.update { it.copy(dialog = Dialog.MassImport) }
    }

    fun openImportEpubDialog() {
        mutableState.update { it.copy(dialog = Dialog.ImportEpub) }
    }

    fun reloadLibraryFromDB() {
        // Force a complete refresh of the library from database
        // This clears the aggressive cache in GetLibraryManga and reloads all data
        screenModelScope.launchIO {
            logcat { "LibraryScreenModel: User requested library reload" }
            // Force refresh bypasses the 2-second throttle
            val library = getLibraryManga.refreshForced()
            logcat { "LibraryScreenModel: Library reloaded with ${library.size} items" }
        }
    }

    /**
     * Find potential duplicate manga in the library based on title similarity.
     * Uses Levenshtein distance for fuzzy matching.
     */
    fun findDuplicates(): List<DuplicateGroup> {
        val favorites = state.value.libraryData.favorites
        if (favorites.size < 2) return emptyList()

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        val processed = mutableSetOf<Long>()

        for (i in favorites.indices) {
            if (favorites[i].id in processed) continue

            val currentManga = favorites[i]
            val currentTitle = normalizeTitle(currentManga.libraryManga.manga.title)
            val group = mutableListOf(currentManga.libraryManga.manga)

            for (j in i + 1 until favorites.size) {
                if (favorites[j].id in processed) continue

                val otherManga = favorites[j]
                val otherTitle = normalizeTitle(otherManga.libraryManga.manga.title)

                if (isSimilar(currentTitle, otherTitle)) {
                    group.add(otherManga.libraryManga.manga)
                    processed.add(favorites[j].id)
                }
            }

            if (group.size > 1) {
                duplicateGroups.add(DuplicateGroup(group.toList()))
                processed.add(favorites[i].id)
            }
        }

        return duplicateGroups
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[\\[\\(].*?[\\]\\)]"), "") // Remove content in brackets/parentheses
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove non-alphanumeric except spaces
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }

    private fun isSimilar(title1: String, title2: String): Boolean {
        // Exact match after normalization
        if (title1 == title2) return true

        // Check if one contains the other
        if (title1.contains(title2) || title2.contains(title1)) return true

        // Use Levenshtein distance for fuzzy matching
        val maxLen = maxOf(title1.length, title2.length)
        if (maxLen == 0) return true
        val distance = levenshteinDistance(title1, title2)
        val similarity = 1.0 - (distance.toDouble() / maxLen)
        return similarity >= 0.8 // 80% similarity threshold
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    fun openDuplicateDetectionDialog() {
        val duplicates = findDuplicates()
        mutableState.update { it.copy(dialog = Dialog.DuplicateDetection(duplicates)) }
    }

    fun selectDuplicatesExceptFirst(groups: List<DuplicateGroup>) {
        val toSelect = groups.flatMap { it.manga.drop(1) }.map { it.id }.toSet()
        mutableState.update { state ->
            state.copy(selection = state.selection + toSelect)
        }
        closeDialog()
    }

    fun selectAllDuplicates(groups: List<DuplicateGroup>) {
        val toSelect = groups.flatMap { it.manga }.map { it.id }.toSet()
        mutableState.update { state ->
            state.copy(selection = state.selection + toSelect)
        }
        closeDialog()
    }

    data class DuplicateGroup(val manga: List<Manga>)

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data object MassImport : Dialog
        data object ImportEpub : Dialog
        data class DuplicateDetection(val duplicates: List<DuplicateGroup>) : Dialog
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Manga>) : Dialog
        data class RemoveChapters(val manga: List<Manga>) : Dialog
        data class MarkReadConfirmation(val read: Boolean) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
        val excludedExtensions: Set<String>,
        val includedTags: Set<String>,
        val excludedTags: Set<String>,
        val filterNoTags: TriState,
    )

    @Immutable
    data class LibraryData(
        val isInitialized: Boolean = false,
        val showSystemCategory: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<LibraryItem> = emptyList(),
        val tracksMap: Map</* Manga */ Long, List<Track>> = emptyMap(),
        val loggedInTrackerIds: Set<Long> = emptySet(),
    ) {
        val favoritesById by lazy { favorites.associateBy { it.id } }
    }

    @Immutable
    data class State(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val selection: Set</* Manga */ Long> = setOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val libraryData: LibraryData = LibraryData(),
        private val activeCategoryIndex: Int = 0,
        private val groupedFavorites: Map<Category, List</* LibraryItem */ Long>> = emptyMap(),
    ) {
        val displayedCategories: List<Category> = groupedFavorites.keys.toList()

        val coercedActiveCategoryIndex = activeCategoryIndex.coerceIn(
            minimumValue = 0,
            maximumValue = displayedCategories.lastIndex.coerceAtLeast(0),
        )

        val activeCategory: Category? = displayedCategories.getOrNull(coercedActiveCategoryIndex)

        val isLibraryEmpty = libraryData.favorites.isEmpty()

        val selectionMode = selection.isNotEmpty()

        val selectedManga by lazy { selection.mapNotNull { libraryData.favoritesById[it]?.libraryManga?.manga } }

        fun getItemsForCategoryId(categoryId: Long?): List<LibraryItem> {
            if (categoryId == null) return emptyList()
            val category = displayedCategories.find { it.id == categoryId } ?: return emptyList()
            return getItemsForCategory(category)
        }

        fun getItemsForCategory(category: Category): List<LibraryItem> {
            return groupedFavorites[category].orEmpty().mapNotNull { libraryData.favoritesById[it] }
        }

        fun getItemCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) groupedFavorites[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = displayedCategories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getItemCountForCategory(category)
                // Whole library count
                else -> libraryData.favorites.size
            }
            return LibraryToolbarTitle(title, count)
        }
    }
}
