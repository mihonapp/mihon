package eu.kanade.tachiyomi.ui.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.cache.LibrarySettingsCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.jsplugin.source.JsSource
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Data class representing extension info in the library settings
 */
data class ExtensionInfo(
    val sourceId: Long,
    val sourceName: String,
    val isStub: Boolean = false,
)

class LibrarySettingsScreenModel(
    val type: LibraryScreenModel.LibraryType = LibraryScreenModel.LibraryType.Manga,
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setDisplayMode: SetDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val librarySettingsCache: LibrarySettingsCache = Injekt.get(),
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    // Cached extension list - NO automatic database subscription
    // Data is loaded only when refreshExtensions() is called
    private val _extensionsFlow = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    val extensionsFlow = _extensionsFlow.asStateFlow()

    // Cached tags with counts - NO automatic database subscription
    // Data is loaded only when refreshTags() is called
    private val _tagsFlow = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val tagsFlow = _tagsFlow.asStateFlow()

    // Cached count of manga with no tags
    private val _noTagsCountFlow = MutableStateFlow(0)
    val noTagsCountFlow = _noTagsCountFlow.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    // Tag search query state
    private val _tagSearchQuery = MutableStateFlow("")
    val tagSearchQuery = _tagSearchQuery.asStateFlow()
    
    // Tag options expanded state
    private val _tagOptionsExpanded = MutableStateFlow(false)
    val tagOptionsExpanded = _tagOptionsExpanded.asStateFlow()
    
    // Flags to track if we've attempted to load from disk cache
    private val _extensionsLoaded = AtomicBoolean(false)
    private val _tagsLoaded = AtomicBoolean(false)

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }

    /**
     * Toggle extension filter.
     * When checked = true, the extension is included (remove from excluded set)
     * When checked = false, the extension is excluded (add to excluded set)
     */
    fun toggleExtensionFilter(sourceId: String, checked: Boolean) {
        val current = libraryPreferences.excludedExtensions().get()
        libraryPreferences.excludedExtensions().set(
            if (checked) {
                // Checked = include = remove from excluded
                current - sourceId
            } else {
                // Unchecked = exclude = add to excluded
                current + sourceId
            },
        )
    }

    /**
     * Check all extensions (include all - clear the excluded set for available extensions)
     */
    fun checkAllExtensions() {
        val availableSourceIds = extensionsFlow.value.map { it.sourceId.toString() }.toSet()
        val current = libraryPreferences.excludedExtensions().get()
        // Remove all available extensions from excluded set
        libraryPreferences.excludedExtensions().set(current - availableSourceIds)
    }

    /**
     * Uncheck all extensions (exclude all - add all available extensions to excluded set)
     */
    fun uncheckAllExtensions() {
        val availableSourceIds = extensionsFlow.value.map { it.sourceId.toString() }.toSet()
        val current = libraryPreferences.excludedExtensions().get()
        // Add all available extensions to excluded set
        libraryPreferences.excludedExtensions().set(current + availableSourceIds)
    }

    // Tag filtering methods
    fun toggleTagIncluded(tag: String) {
        val included = libraryPreferences.includedTags().get()
        val excluded = libraryPreferences.excludedTags().get()
        
        when {
            tag in included -> {
                // Currently included -> move to excluded
                libraryPreferences.includedTags().set(included - tag)
                libraryPreferences.excludedTags().set(excluded + tag)
            }
            tag in excluded -> {
                // Currently excluded -> remove filter
                libraryPreferences.excludedTags().set(excluded - tag)
            }
            else -> {
                // Not filtered -> include
                libraryPreferences.includedTags().set(included + tag)
            }
        }
    }

    fun clearAllTagFilters() {
        libraryPreferences.includedTags().set(emptySet())
        libraryPreferences.excludedTags().set(emptySet())
        libraryPreferences.filterNoTags().set(TriState.DISABLED)
    }

    fun toggleNoTagsFilter() {
        toggleFilter { libraryPreferences.filterNoTags() }
    }
    
    fun setTagSearchQuery(query: String) {
        _tagSearchQuery.value = query
    }
    
    fun toggleTagOptions() {
        _tagOptionsExpanded.value = !_tagOptionsExpanded.value
    }
    
    /**
     * Refresh extensions list from database or cache.
     * This is the ONLY way to load extension data - no auto-subscription.
     */
    fun refreshExtensions(forceRefresh: Boolean = false) {
        if (_isLoading.value) return
        screenModelScope.launchIO {
            _isLoading.value = true
            try {
                // Try loading from disk cache first if not forced and not loaded yet
                if (!forceRefresh && !_extensionsLoaded.get()) {
                    val cached = librarySettingsCache.loadExtensions()
                    if (cached != null && cached.isNotEmpty()) {
                        _extensionsFlow.value = cached
                        _extensionsLoaded.set(true)
                        _isLoading.value = false
                        return@launchIO
                    }
                }

                // If no cache or forced refresh, load from DB using ultra-lightweight query
                // This only fetches source IDs, avoiding the expensive libraryView JOIN
                val sourceIds = getLibraryManga.awaitSourceIds()
                val extensions = sourceIds.mapNotNull { sourceId ->
                    val source = sourceManager.getOrStub(sourceId)
                    val isNovel = source.isNovelSource()
                    val isStub = source is StubSource
                    val shouldInclude = when (type) {
                        LibraryScreenModel.LibraryType.All -> true
                        LibraryScreenModel.LibraryType.Manga -> !isNovel
                        LibraryScreenModel.LibraryType.Novel -> isNovel
                    }
                    if (shouldInclude) {
                        // Add (JS) suffix for JS plugin sources
                        val displayName = if (source is JsSource) "${source.name} (JS)" else source.name
                        ExtensionInfo(sourceId, displayName, isStub)
                    } else null
                }.sortedWith(compareBy({ !it.isStub }, { it.sourceName })) // Stub sources first, then alphabetically
                
                _extensionsFlow.value = extensions
                librarySettingsCache.saveExtensions(extensions)
                _extensionsLoaded.set(true)
            } catch (e: Exception) {
                // Ignore error, keep empty list
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh tags list and counts from database or cache.
     * This is the ONLY way to load tag data - no auto-subscription.
     * Tags are filtered by content type (manga/novel) based on the library type.
     */
    fun refreshTags(forceRefresh: Boolean = false) {
        if (_isLoading.value) return
        screenModelScope.launchIO {
            _isLoading.value = true
            try {
                logcat(LogPriority.INFO) { "LibrarySettingsScreenModel: refreshTags(forceRefresh=$forceRefresh, type=$type)" }
                
                // Skip cache when filtering by type - we need fresh filtered data
                // Cache is only useful for All type
                if (!forceRefresh && !_tagsLoaded.get() && type == LibraryScreenModel.LibraryType.All) {
                    val cached = librarySettingsCache.loadTags()
                    if (cached != null) {
                        logcat(LogPriority.DEBUG) { "LibrarySettingsScreenModel: Loaded ${cached.first.size} tags from cache" }
                        _tagsFlow.value = cached.first
                        _noTagsCountFlow.value = cached.second
                        _tagsLoaded.set(true)
                        _isLoading.value = false
                        return@launchIO
                    }
                }

                // Load from DB with source IDs for filtering by content type
                logcat(LogPriority.INFO) { "LibrarySettingsScreenModel: Loading tags from database (lightweight query with source filtering)..." }
                val genresList = getLibraryManga.awaitGenresWithSource()
                logcat(LogPriority.DEBUG) { "LibrarySettingsScreenModel: Got ${genresList.size} manga from library" }
                
                // Calculate tag counts, filtering by type
                val tagCounts = mutableMapOf<String, Int>()
                var noTagsCount = 0
                genresList.forEach { (_, sourceId, genres) ->
                    // Filter by content type
                    val source = sourceManager.getOrStub(sourceId)
                    val isNovel = source.isNovelSource()
                    val shouldInclude = when (type) {
                        LibraryScreenModel.LibraryType.All -> true
                        LibraryScreenModel.LibraryType.Manga -> !isNovel
                        LibraryScreenModel.LibraryType.Novel -> isNovel
                    }
                    
                    if (shouldInclude) {
                        if (genres.isNullOrEmpty()) {
                            noTagsCount++
                        } else {
                            genres.forEach { tag ->
                                val normalizedTag = tag.trim()
                                if (normalizedTag.isNotBlank()) {
                                    tagCounts[normalizedTag] = (tagCounts[normalizedTag] ?: 0) + 1
                                }
                            }
                        }
                    }
                }
                
                logcat(LogPriority.INFO) { "LibrarySettingsScreenModel: Found ${tagCounts.size} unique tags, $noTagsCount items without tags (type=$type)" }
                
                val tagsList = tagCounts.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }
                
                _tagsFlow.value = tagsList
                _noTagsCountFlow.value = noTagsCount
                
                // Only cache for All type
                if (type == LibraryScreenModel.LibraryType.All) {
                    librarySettingsCache.saveTags(tagsList, noTagsCount)
                }
                _tagsLoaded.set(true)
                logcat(LogPriority.INFO) { "LibrarySettingsScreenModel: refreshTags completed" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "LibrarySettingsScreenModel: Error refreshing tags" }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
