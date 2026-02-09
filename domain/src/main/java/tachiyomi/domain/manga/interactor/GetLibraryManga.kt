package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibraryMangaForUpdate
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Interactor for getting library manga with AGGRESSIVE caching.
 * 
 * The libraryView query is extremely expensive (~20+ seconds for 27k items).
 * SQLDelight's reactive Flow re-runs the query on ANY table change, which is unacceptable.
 * 
 * This class uses a manual StateFlow that is ONLY refreshed when explicitly requested,
 * preventing the query from running hundreds of times during normal app usage.
 */
class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    
    // Cached library data - ONLY updated via refresh()
    private val _libraryState = MutableStateFlow<List<LibraryManga>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private var isInitialized = false
    private var lastRefreshTime = 0L
    
    // Minimum time between refreshes (prevent spam)
    private val minRefreshIntervalMs = 2000L
    
    init {
        // Initial load on creation
        scope.launch {
            refreshInternal(force = true)
        }
    }

    /**
     * Get the current cached library synchronously (may be empty if not yet loaded).
     */
    suspend fun await(): List<LibraryManga> {
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let { "${it.className}.${it.methodName}" } ?: "unknown"
        logcat(LogPriority.DEBUG) { "GetLibraryManga.await() called by: $caller" }
        // If not initialized, wait for initial load
        if (!isInitialized) {
            logcat(LogPriority.INFO) { "GetLibraryManga.await() triggering initial load (called by $caller)" }
            refreshInternal(force = true)
        }
        return _libraryState.value
    }
    
    /**
     * Check if library is currently loading
     */
    fun isLoading(): Flow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Force a refresh of the library cache.
     * This is the ONLY way to trigger a new database query.
     * Use sparingly - only when user explicitly requests refresh or after bulk operations.
     * 
     * Note: This launches the refresh in an isolated scope so it won't be cancelled
     * if the calling scope is cancelled (e.g., when navigating away from a screen).
     */
    fun refresh() {
        scope.launch {
            refreshInternal(force = false)
        }
    }

    /**
     * Force a refresh and bypass the minimum refresh interval.
     * Use when user explicitly requests a reload (e.g., after backup restore).
     * Returns the updated library list.
     * 
     * This is a suspend function that waits for the refresh to complete.
     */
    suspend fun refreshForced(): List<LibraryManga> {
        refreshInternal(force = true)
        return _libraryState.value
    }

    /**
     * Await refresh - waits for the current refresh to complete.
     * Use this when you need to ensure the library is up-to-date before proceeding.
     */
    suspend fun awaitRefresh() {
        refreshInternal(force = false)
    }

    /**
     * Apply category updates to the in-memory library list without a full DB refresh.
     * This keeps UI responsive for small, targeted changes.
     */
    suspend fun applyCategoryUpdates(
        mangaIds: List<Long>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        if (mangaIds.isEmpty()) return
        mutex.withLock {
            val idSet = mangaIds.toSet()
            _libraryState.value = _libraryState.value.map { item ->
                if (item.id !in idSet) return@map item

                val updated = item.categories.toMutableSet()
                addCategories.forEach { updated.add(it) }
                removeCategories.forEach { updated.remove(it) }
                item.copy(categories = updated.toList())
            }
        }
    }
    
    private suspend fun refreshInternal(force: Boolean) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && (now - lastRefreshTime) < minRefreshIntervalMs) {
                logcat(LogPriority.DEBUG) { "GetLibraryManga: Skipping refresh (too soon, ${now - lastRefreshTime}ms since last)" }
                return
            }
            
            // Always invalidate the repository's in-memory cache so refresh returns fresh data
            mangaRepository.invalidateLibraryCache()
            
            _isLoading.value = true
            val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let { "${it.className}.${it.methodName}" } ?: "unknown"
            logcat(LogPriority.INFO) { "GetLibraryManga: Refreshing library cache (force=$force, caller=$caller)" }
            val startTime = System.currentTimeMillis()
            
            try {
                val library = mangaRepository.getLibraryManga()
                _libraryState.value = library
                isInitialized = true
                lastRefreshTime = System.currentTimeMillis()
                
                val duration = System.currentTimeMillis() - startTime
                logcat(LogPriority.INFO) { "GetLibraryManga: Refresh complete in ${duration}ms, ${library.size} items" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "GetLibraryManga: Failed to refresh library" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get a lightweight list of library manga for update filtering.
     * This query is faster as it skips heavy fields like description, genre, etc.
     */
    suspend fun awaitForUpdate(): List<LibraryMangaForUpdate> {
        return mangaRepository.getLibraryMangaForUpdate()
    }

    /**
     * Get only genres for tag counting - much faster than await().
     * This avoids the expensive libraryView JOIN and only fetches _id + genre from mangas table.
     */
    suspend fun awaitGenresOnly(): List<Pair<Long, List<String>?>> {
        return mangaRepository.getFavoriteGenres()
    }

    /**
     * Get genres with source ID for tag counting filtered by content type.
     * This avoids the expensive libraryView JOIN and only fetches _id + source + genre from mangas table.
     */
    suspend fun awaitGenresWithSource(): List<Triple<Long, Long, List<String>?>> {
        return mangaRepository.getFavoriteGenresWithSource()
    }

    /**
     * Get just the distinct source IDs from favorites - ultra-lightweight for extension listing.
     * This avoids the expensive libraryView JOIN and only fetches source IDs.
     */
    suspend fun awaitSourceIds(): List<Long> {
        return mangaRepository.getFavoriteSourceIds()
    }

    /**
     * Subscribe to library changes. Returns a StateFlow that is ONLY updated when refresh() is called.
     * This prevents SQLDelight from re-running the expensive query on every table change.
     */
    fun subscribe(): Flow<List<LibraryManga>> {
        return _libraryState.asStateFlow()
    }
}
