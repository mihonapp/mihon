package mihon.desktop.ui.upcoming

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mihon.desktop.category.DesktopCategory
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.ui.library.TriStateFilter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * Observes the favorite library and builds the month/day grouping used by the Upcoming screen.
 *
 * A chapter is included when its `dateUpload` is strictly in the future. Desktop does not compute
 * an "expected next update" timestamp today, so chapters without a source-provided future upload
 * date cannot be surfaced and the screen shows its empty state instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingPresenter(
    private val repository: LibraryRepository,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val preferences: DesktopPreferenceStore? = null,
) : AutoCloseable {
    private val presenterJob = SupervisorJob(scope.coroutineContext[Job])
    private val presenterScope = CoroutineScope(scope.coroutineContext + presenterJob)
    private val initialMonth = YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zoneId))

    private val selectedMonth = MutableStateFlow(initialMonth)
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val categoryFilters = MutableStateFlow(loadCategoryFilters())
    private val isFilterDialogOpen = MutableStateFlow(false)
    private val retryRequest = MutableStateFlow(0L)

    private val libraryFlow: Flow<List<LibraryManga>> = flow { emitAll(repository.observeLibrary()) }
        .catch { emit(emptyList()) }

    private val entriesState: Flow<EntriesState> = combine(retryRequest, libraryFlow) { _, library -> library }
        .flatMapLatest { library ->
            if (library.isEmpty()) {
                flowOf(EntriesState.Loaded(emptyList()))
            } else {
                combine(library.map { observeMangaData(it) }) { items -> items.toList() }
                    .map<List<UpcomingMangaData>, EntriesState> { data -> EntriesState.Loaded(buildEntries(data)) }
                    .onStart { emit(EntriesState.Loading) }
                    .catch { error ->
                        emit(EntriesState.Failed(error.message ?: "Unable to load upcoming chapters"))
                    }
            }
        }
        .flowOn(Dispatchers.IO)

    private val categoriesState: Flow<List<DesktopCategory>> = flow { emitAll(repository.observeCategories()) }
        .map { records -> records.map(CategoryRecord::toDesktopCategory) }
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    private val coreState: Flow<CoreState> = combine(
        entriesState,
        selectedMonth,
        selectedDate,
        categoryFilters,
        isFilterDialogOpen,
    ) { entries, month, date, filters, dialog ->
        CoreState(
            entriesState = entries,
            selectedMonth = month,
            selectedDate = date,
            categoryFilters = filters,
            isFilterDialogOpen = dialog,
        )
    }

    val state: StateFlow<UpcomingUiState> = combine(coreState, categoriesState) { core, categories ->
        core.toUiState(categories)
    }.stateIn(
        scope = presenterScope,
        started = SharingStarted.Eagerly,
        initialValue = UpcomingUiState(
            loading = true,
            selectedMonth = initialMonth,
            today = currentDate(),
        ),
    )

    private fun observeMangaData(manga: LibraryManga): Flow<UpcomingMangaData> {
        // observeManga is used both for its category fallback and as a refresh signal when a
        // manga's category links change. Fakes are free to return a null/empty flow.
        val details = flow { emitAll(repository.observeManga(manga.id)) }
            .catch { emit(null) }
        val chapters = flow { emitAll(repository.observeChapters(manga.id)) }
            .catch { emit(emptyList()) }
        return combine(details, chapters) { detail, rows ->
            UpcomingMangaData(
                manga = manga,
                chapters = rows,
                categoryIds = detail?.categories?.map { it.id }?.toSet().orEmpty(),
            )
        }
    }

    private fun buildEntries(data: List<UpcomingMangaData>): List<UpcomingChapterEntry> {
        val now = clock()
        val sourceNames = runCatching { repository.allSourcesSnapshot() }
            .getOrDefault(emptyList())
            .associate { it.sourceId to it.name }
        val categoryLinks = runCatching { repository.mangaCategoryLinksSnapshot() }
            .getOrDefault(emptyMap())

        return data.flatMap { item ->
            item.chapters.mapNotNull { chapter ->
                if (chapter.dateUpload <= now) return@mapNotNull null
                UpcomingChapterEntry(
                    mangaId = item.manga.id,
                    mangaTitle = item.manga.title,
                    mangaThumbnailUrl = item.manga.thumbnailUrl,
                    sourceId = item.manga.sourceId,
                    sourceName = sourceNames[item.manga.sourceId],
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    chapterNumber = chapter.chapterNumber,
                    dateUpload = chapter.dateUpload,
                    categoryIds = categoryLinks[item.manga.id]?.toSet() ?: item.categoryIds,
                )
            }
        }
    }

    private fun CoreState.toUiState(categories: List<DesktopCategory>): UpcomingUiState {
        val loadedEntries = (entriesState as? EntriesState.Loaded)?.entries.orEmpty()
        val included = categoryFilters.filterValues { it == TriStateFilter.Include }.keys
        val excluded = categoryFilters.filterValues { it == TriStateFilter.Exclude }.keys
        val filteredEntries = loadedEntries.filter { entry ->
            val matchesIncluded = included.isEmpty() || entry.categoryIds.any { it in included }
            val matchesExcluded = excluded.isEmpty() || entry.categoryIds.none { it in excluded }
            matchesIncluded && matchesExcluded
        }
        val entriesByDate = filteredEntries
            .sortedWith(UpcomingEntryComparator)
            .groupBy { it.dateIn(zoneId) }

        return UpcomingUiState(
            loading = entriesState is EntriesState.Loading,
            errorMessage = (entriesState as? EntriesState.Failed)?.message,
            selectedMonth = selectedMonth,
            selectedDate = selectedDate,
            today = currentDate(),
            entriesByDate = entriesByDate,
            hasAnyUpcomingBeforeFilters = loadedEntries.isNotEmpty(),
            categories = categories,
            categoryFilters = categoryFilters,
            isFilterDialogOpen = isFilterDialogOpen,
        )
    }

    fun setSelectedMonth(month: YearMonth) {
        selectedMonth.value = month
        selectedDate.value = null
    }

    fun previousMonth() {
        setSelectedMonth(selectedMonth.value.minusMonths(1))
    }

    fun nextMonth() {
        setSelectedMonth(selectedMonth.value.plusMonths(1))
    }

    fun selectDate(date: LocalDate?) {
        selectedDate.value = date
    }

    fun selectDay(date: LocalDate) {
        selectedDate.value = date
    }

    fun clearSelectedDate() {
        selectedDate.value = null
    }

    fun setFilterDialogOpen(open: Boolean) {
        isFilterDialogOpen.value = open
    }

    fun toggleFilterDialog() {
        isFilterDialogOpen.update { !it }
    }

    /** Cycles one category through Off -> Include -> Exclude -> Off. */
    fun cycleCategory(categoryId: Long) {
        val current = categoryFilters.value
        val next = when (current[categoryId] ?: TriStateFilter.Disabled) {
            TriStateFilter.Disabled -> TriStateFilter.Include
            TriStateFilter.Include -> TriStateFilter.Exclude
            TriStateFilter.Exclude -> TriStateFilter.Disabled
        }
        val updated = current.toMutableMap()
        if (next == TriStateFilter.Disabled) {
            updated.remove(categoryId)
        } else {
            updated[categoryId] = next
        }
        categoryFilters.value = updated.toMap()
        persistCategoryFilters(categoryFilters.value)
    }

    fun clearFilters() {
        categoryFilters.value = emptyMap()
        persistCategoryFilters(emptyMap())
    }

    fun retry() {
        retryRequest.update { it + 1 }
    }

    override fun close() {
        presenterScope.cancel()
    }

    private fun currentDate(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zoneId).toLocalDate()

    private fun loadCategoryFilters(): Map<Long, TriStateFilter> {
        val store = preferences ?: return emptyMap()
        val included = parseLongSet(store.property(FILTER_INCLUDED_CATEGORIES_KEY))
        val excluded = parseLongSet(store.property(FILTER_EXCLUDED_CATEGORIES_KEY))
        return buildMap {
            included.forEach { put(it, TriStateFilter.Include) }
            excluded.forEach { put(it, TriStateFilter.Exclude) }
        }
    }

    private fun persistCategoryFilters(filters: Map<Long, TriStateFilter>) {
        val store = preferences ?: return
        store.update {
            setProperty(
                FILTER_INCLUDED_CATEGORIES_KEY,
                filters.filterValues { it == TriStateFilter.Include }.keys.joinToString(","),
            )
            setProperty(
                FILTER_EXCLUDED_CATEGORIES_KEY,
                filters.filterValues { it == TriStateFilter.Exclude }.keys.joinToString(","),
            )
        }
    }

    private data class UpcomingMangaData(
        val manga: LibraryManga,
        val chapters: List<LibraryChapter>,
        val categoryIds: Set<Long> = emptySet(),
    )

    private data class CoreState(
        val entriesState: EntriesState,
        val selectedMonth: YearMonth,
        val selectedDate: LocalDate?,
        val categoryFilters: Map<Long, TriStateFilter>,
        val isFilterDialogOpen: Boolean,
    )

    private sealed interface EntriesState {
        data object Loading : EntriesState
        data class Loaded(val entries: List<UpcomingChapterEntry>) : EntriesState
        data class Failed(val message: String) : EntriesState
    }

    private companion object {
        const val FILTER_INCLUDED_CATEGORIES_KEY = "upcoming.filter_included_categories"
        const val FILTER_EXCLUDED_CATEGORIES_KEY = "upcoming.filter_excluded_categories"

        val UpcomingEntryComparator: Comparator<UpcomingChapterEntry> = compareBy(
            { it.dateUpload },
            { it.mangaTitle.lowercase(Locale.ROOT) },
            { it.chapterNumber },
            { it.chapterId },
        )
    }
}

private fun CategoryRecord.toDesktopCategory(): DesktopCategory = DesktopCategory(
    id = id,
    name = name,
    order = sortOrder,
    flags = flags,
)

private fun parseLongSet(value: String?): Set<Long> = value
    ?.split(',')
    ?.mapNotNull { it.trim().toLongOrNull() }
    ?.toSet()
    .orEmpty()
