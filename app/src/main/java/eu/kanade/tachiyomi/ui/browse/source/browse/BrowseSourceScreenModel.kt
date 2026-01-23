package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.ManageFilterPresets
import eu.kanade.domain.source.model.FilterPreset
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFavoritesEntry
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

class BrowseSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getRemoteManga: GetRemoteManga = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val getFavoritesEntry: GetFavoritesEntry = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    private val manageFilterPresets: ManageFilterPresets = Injekt.get(),
    private val translationEngineManager: TranslationEngineManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
) : StateScreenModel<BrowseSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    val titleMaxLines by libraryPreferences.titleMaxLines().asState(screenModelScope)

    val source = sourceManager.getOrStub(sourceId)

    // Filter Preset Management - declared before init to avoid NPE
    private val _filterPresets = MutableStateFlow<List<FilterPreset>>(emptyList())
    val filterPresets: StateFlow<List<FilterPreset>> = _filterPresets.asStateFlow()

    // Cached categories for fast access - loaded once and kept in memory
    private val _cachedCategories = MutableStateFlow<List<Category>>(emptyList())
    
    // Remember last selected category IDs for re-use
    private var lastSelectedCategoryIds: List<Long> = emptyList()

    // Auto-apply filter presets as a StateFlow that updates when preference changes
    val autoApplyFilterPresets: StateFlow<Boolean> = sourcePreferences.autoApplyFilterPresets()
        .changes()
        .stateIn(screenModelScope, SharingStarted.Lazily, sourcePreferences.autoApplyFilterPresets().get())

    init {
        // Load filter presets from storage
        refreshFilterPresets()
        
        // Preload categories in background for fast access when adding favorites
        screenModelScope.launch {
            _cachedCategories.value = getCategories.subscribe()
                .firstOrNull()
                ?.filterNot { it.isSystemCategory }
                .orEmpty()
        }

        if (source is CatalogueSource) {
            mutableState.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                    listing = Listing.Search(query, source.getFilterList())
                }

                it.copy(
                    listing = listing,
                    filters = source.getFilterList(),
                    toolbarQuery = query,
                )
            }

            // Apply default preset if auto-apply is enabled (async)
            screenModelScope.launch {
                applyDefaultPresetIfEnabled()
            }
        }

        if (!getIncognitoState.await(source.id)) {
            sourcePreferences.lastUsedSource().set(source.id)
        }
    }

    /**
     * Flow of Pager flow tied to [State.listing] and [State.filters].
     *
     * Filters are stored in [State.filters] (the single source of truth). Historically,
     * [Listing.Search.filters] could get out of sync, causing default presets to not apply
     * until the user performed a new search.
     *
     * Note: We use hashCode for comparison because FilterList.equals() always returns false
     * to force recomposition, but we only want new Pagers when filters actually change.
     * 
     * Optimization: Instead of creating individual DB subscriptions per manga item,
     * we maintain a single cached flow of library manga for this source and do
     * in-memory lookups to update favorite status.
     */
    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems().get()
    private fun normalizeUrl(url: String): String = url.trimEnd('/').substringBefore('#')
    
    // Cached library manga for this source - single subscription instead of per-item
    private val libraryMangaForSource: StateFlow<Map<String, Manga>> = getFavoritesEntry.subscribe(sourceId)
        .map { favorites -> 
            favorites.associateBy { normalizeUrl(it.url) }
        }
        .stateIn(ioCoroutineScope, SharingStarted.Eagerly, emptyMap())
    
    val mangaPagerFlowFlow = state
        .map { Triple(it.listing.query, it.filters, it.filters.hashCode()) }
        .distinctUntilChanged { old, new -> old.first == new.first && old.third == new.third }
        .map { (query, filters, _) ->
            logcat(LogPriority.DEBUG) { "Creating new Pager for query='$query', filters=${filters.hashCode()}" }
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteManga(sourceId, query ?: "", filters)
            }.flow.map { pagingData ->
                pagingData.map { manga ->
                    // Normalize URL to prevent duplicates from trailing slashes/fragments
                    val normalizedUrl = normalizeUrl(manga.url)
                    val normalizedManga = if (normalizedUrl != manga.url) {
                        manga.copy(url = normalizedUrl)
                    } else {
                        manga
                    }
                    // Use cached library lookup instead of individual DB subscription
                    // This StateFlow combines remote manga with library status from cache
                    libraryMangaForSource
                        .map { libraryMap -> 
                            libraryMap[normalizedUrl] ?: normalizedManga
                        }
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns()
        } else {
            libraryPreferences.portraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    fun resetFilters() {
        if (source !is CatalogueSource) return

        mutableState.update { it.copy(filters = source.getFilterList()) }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: FilterList) {
        if (source !is CatalogueSource) return
        logcat(LogPriority.DEBUG) { "setFilters called, filters hashCode: ${filters.hashCode()}" }

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(query: String? = null, filters: FilterList? = null) {
        if (source !is CatalogueSource) return
        logcat(LogPriority.DEBUG) { "search called: query='$query', filters=${filters?.hashCode()}" }

        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

        val newFilters = filters ?: input.filters
        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = newFilters,
                ),
                filters = newFilters, // Ensure state.filters is also updated
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun searchGenre(genreName: String) {
        if (source !is CatalogueSource) return

        val defaultFilters = source.getFilterList()
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is SourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is SourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is SourceModelFilter.TriState -> filter.state = 1
                            is SourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is SourceModelFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        mutableState.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
            )
        }
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        screenModelScope.launch {
            val normalizedUrl = normalizeUrl(manga.url)
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
                url = normalizedUrl,
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launch {
            // Use cached categories for instant response
            val categories = _cachedCategories.value.ifEmpty { getCategories() }
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)

                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)

                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    // Use last selected categories if available, otherwise fetch from DB
                    val preselectedIds = if (lastSelectedCategoryIds.isNotEmpty()) {
                        lastSelectedCategoryIds
                    } else {
                        getCategories.await(manga.id).map { it.id }
                    }
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Save selected category IDs for re-use in subsequent category selections.
     */
    fun rememberCategorySelection(categoryIds: List<Long>) {
        lastSelectedCategoryIds = categoryIds
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        // Use cached categories for fast access - they're preloaded in init
        val cached = _cachedCategories.value
        return if (cached.isNotEmpty()) {
            cached
        } else {
            // Fallback to fresh query if cache not yet populated
            getCategories.subscribe()
                .firstOrNull()
                ?.filterNot { it.isSystemCategory }
                .orEmpty()
        }
    }

    suspend fun getDuplicateLibraryManga(manga: Manga): List<MangaWithChapterCount> {
        return getDuplicateLibraryManga.invoke(manga)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun openPresetSheet() {
        // Refresh presets from storage when opening sheet
        refreshFilterPresets()
        setDialog(Dialog.FilterPresets)
    }

    private fun refreshFilterPresets() {
        _filterPresets.value = manageFilterPresets.getPresets(sourceId).presets
    }

    fun saveFilterPreset(name: String, setAsDefault: Boolean) {
        if (source !is CatalogueSource) return

        logcat(LogPriority.DEBUG) {
            "BrowseSource: saveFilterPreset name=$name, setAsDefault=$setAsDefault, sourceId=$sourceId"
        }
        manageFilterPresets.savePreset(
            sourceId = sourceId,
            name = name,
            filters = state.value.filters,
            setAsDefault = setAsDefault,
        )
        // Immediately refresh the presets list
        refreshFilterPresets()
        logcat(LogPriority.INFO) { "BrowseSource: Preset '$name' saved" }
    }

    fun loadFilterPreset(presetId: Long) {
        if (source !is CatalogueSource) return

        logcat(LogPriority.DEBUG) { "BrowseSource: loadFilterPreset presetId=$presetId, sourceId=$sourceId" }
        val presetState = manageFilterPresets.loadPresetState(sourceId, presetId)
        if (presetState != null) {
            logcat(LogPriority.DEBUG) { "BrowseSource: Loaded preset state, applying..." }
            val filters = source.getFilterList()
            ManageFilterPresets.applyPresetState(filters, presetState)
            setFilters(filters)
            logcat(LogPriority.INFO) { "BrowseSource: Preset applied successfully" }
        } else {
            logcat(LogPriority.WARN) { "BrowseSource: Preset state is null for presetId=$presetId" }
        }
    }

    fun deleteFilterPreset(presetId: Long) {
        logcat(LogPriority.DEBUG) { "BrowseSource: deleteFilterPreset presetId=$presetId" }
        manageFilterPresets.deletePreset(sourceId, presetId)
        // Immediately refresh the presets list
        refreshFilterPresets()
        logcat(LogPriority.INFO) { "BrowseSource: Preset deleted" }
    }

    fun setDefaultFilterPreset(presetId: Long?) {
        logcat(LogPriority.DEBUG) { "BrowseSource: setDefaultFilterPreset presetId=$presetId" }
        manageFilterPresets.setDefaultPreset(sourceId, presetId)
        // Immediately refresh the presets list
        refreshFilterPresets()
        logcat(LogPriority.INFO) { "BrowseSource: Default preset set" }
    }

    fun getAutoApplyPresets(): Boolean {
        return manageFilterPresets.getAutoApplyEnabled()
    }

    fun setAutoApplyPresets(enabled: Boolean) {
        logcat(LogPriority.DEBUG) { "BrowseSource: setAutoApplyPresets=$enabled" }
        manageFilterPresets.setAutoApplyEnabled(enabled)
    }

    private fun applyDefaultPresetIfEnabled() {
        if (source !is CatalogueSource) return
        if (!manageFilterPresets.getAutoApplyEnabled()) return

        logcat(LogPriority.DEBUG) { "BrowseSource: applyDefaultPresetIfEnabled sourceId=$sourceId" }
        val presetState = manageFilterPresets.getDefaultPresetState(sourceId)
        if (presetState != null) {
            logcat(LogPriority.DEBUG) { "BrowseSource: Found default preset, applying..." }
            val filters = source.getFilterList()
            ManageFilterPresets.applyPresetState(filters, presetState)
            mutableState.update { it.copy(filters = filters) }
            // Force a search with the new filters if we are in a search listing
            if (state.value.listing is Listing.Search) {
                search(state.value.listing.query, filters)
            }
            logcat(LogPriority.INFO) { "BrowseSource: Default preset applied" }
        } else {
            logcat(LogPriority.DEBUG) { "BrowseSource: No default preset found" }
        }
    }

    fun setDialog(dialog: Dialog?) {
        logcat(LogPriority.DEBUG) { "setDialog: $dialog" }
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(
            override val query: String?,
            override val filters: FilterList,
        ) : Listing(query = query, filters = filters)

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteManga.QUERY_POPULAR -> Popular
                    GetRemoteManga.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data object FilterPresets : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    // Translation
    private var translationJob: kotlinx.coroutines.Job? = null

    fun toggleTranslateTitles() {
        val newState = !state.value.translateTitles
        logcat(LogPriority.DEBUG) { "toggleTranslateTitles: $newState" }
        mutableState.update { it.copy(translateTitles = newState) }

        if (newState) {
            // Start translating titles when enabled
            translateCurrentTitles()
        } else {
            // Cancel any ongoing translation
            translationJob?.cancel()
        }
    }

    /**
     * Translate all currently loaded manga titles
     */
    private fun translateCurrentTitles() {
        translationJob?.cancel()
        translationJob = screenModelScope.launchIO {
            try {
                val engine = translationEngineManager.getSelectedEngine()
                val targetLang = translationPreferences.targetLanguage().get()
                val sourceLang = translationPreferences.sourceLanguage().get()

                // Get current manga list from pager (this is a simplified approach)
                // We'll translate titles as they become available
                logcat { "Translation enabled: engine=${engine.name}, target=$targetLang" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to initialize translation: ${e.message}" }
            }
        }
    }

    private val translationChannel = Channel<Manga>(Channel.UNLIMITED)

    init {
        screenModelScope.launchIO {
            while (isActive) {
                val batch = mutableListOf<Manga>()
                try {
                    // Wait for first item
                    val first = translationChannel.receive()
                    batch.add(first)

                    // Wait a bit to collect more items for the batch
                    delay(500)

                    // Drain channel up to batch size
                    while (batch.size < 10) {
                        val next = translationChannel.tryReceive().getOrNull()
                        if (next != null) {
                            batch.add(next)
                        } else {
                            break
                        }
                    }

                    if (!state.value.translateTitles) continue

                    try {
                        val engine = translationEngineManager.getSelectedEngine()
                        val targetLang = translationPreferences.targetLanguage().get()
                        val sourceLang = translationPreferences.sourceLanguage().get()

                        // Filter out already translated titles
                        val toTranslate = batch.filter { manga ->
                            !state.value.translatedTitles.containsKey(manga.id)
                        }.distinctBy { it.id }

                        if (toTranslate.isNotEmpty()) {
                            val titles = toTranslate.map { it.title }
                            val result = engine.translate(titles, sourceLang, targetLang)

                            when (result) {
                                is TranslationResult.Success -> {
                                    val newTranslations = toTranslate.mapIndexed { index, manga ->
                                        manga.id to result.translatedTexts.getOrNull(index).orEmpty()
                                    }.toMap()

                                    mutableState.update { state ->
                                        state.copy(
                                            translatedTitles = state.translatedTitles + newTranslations,
                                        )
                                    }
                                }
                                is TranslationResult.Error -> {
                                    logcat(LogPriority.ERROR) { "Translation failed: ${result.message}" }
                                }
                            }

                            // Rate limiting delay
                            if (engine.isRateLimited) {
                                delay(translationPreferences.rateLimitDelayMs().get().toLong())
                            }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "Failed to translate titles: ${e.message}" }
                    }
                } catch (e: Exception) {
                    // Ignore channel closed or other errors
                }
            }
        }
    }

    /**
     * Translate a single manga title
     */
    fun translateManga(manga: Manga) {
        if (!state.value.translateTitles) return
        if (state.value.translatedTitles.containsKey(manga.id)) return
        translationChannel.trySend(manga)
    }

    /**
     * Translate a batch of manga titles (Deprecated, use translateManga)
     */
    fun translateTitles(mangaList: List<Manga>) {
        mangaList.forEach { translateManga(it) }
    }

    /**
     * Get the display title for a manga (translated if available and enabled)
     */
    fun getDisplayTitle(manga: Manga): String {
        if (!state.value.translateTitles) return manga.title
        return state.value.translatedTitles[manga.id] ?: manga.title
    }

    // Mass Import / Selection Mode
    fun toggleSelectionMode() {
        mutableState.update { it.copy(selectionMode = !it.selectionMode, selection = emptySet()) }
    }

    fun toggleSelection(manga: Manga) {
        mutableState.update { state ->
            val newSelection = state.selection.toMutableSet()
            if (manga in newSelection) {
                newSelection.remove(manga)
            } else {
                newSelection.add(manga)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(mangaList: List<Manga>) {
        mutableState.update { state ->
            state.copy(selection = state.selection + mangaList.filter { !it.favorite })
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptySet()) }
    }

    fun openMassImportDialog() {
        // Use the library's mass import dialog which is comprehensive and handles full URL import
        // Just clear selection and exit, as the library dialog will handle the import flow
        mutableState.update { it.copy(selection = emptySet(), selectionMode = false) }
        setDialog(null)
    }

    fun massImportToCategory(categoryId: Long?) {
        screenModelScope.launchIO {
            val selection = state.value.selection
            selection.forEach { manga ->
                // Add to favorites
                val newManga = manga.copy(
                    favorite = true,
                    dateAdded = java.time.Instant.now().toEpochMilli(),
                )
                updateManga.await(newManga.toMangaUpdate())
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)

                // Set category if specified
                if (categoryId != null && categoryId != 0L) {
                    setMangaCategories.await(manga.id, listOf(categoryId))
                }
            }
            // Clear selection and exit selection mode
            mutableState.update { it.copy(selection = emptySet(), selectionMode = false) }
            setDialog(null)
        }
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        val selectionMode: Boolean = false,
        val selection: Set<Manga> = emptySet(),
        val translateTitles: Boolean = false,
        val translatedTitles: Map<Long, String> = emptyMap(),
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
