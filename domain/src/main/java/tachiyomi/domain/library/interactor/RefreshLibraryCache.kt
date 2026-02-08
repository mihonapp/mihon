package tachiyomi.domain.library.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Interactor for refreshing the library cache table.
 * 
 * The library_cache table stores pre-computed aggregates (chapter counts, read counts, etc.)
 * that would otherwise require expensive JOINs on every query.
 * 
 * Triggers automatically update the cache on most operations, but this interactor
 * can be used to:
 * - Force a full cache refresh on app startup
 * - Refresh cache after bulk operations
 * - Verify cache integrity
 */
class RefreshLibraryCache(
    private val mangaRepository: MangaRepository,
) {
    /**
     * Refresh the entire library cache.
     * This is a relatively expensive operation and should be called sparingly.
     */
    suspend fun await() {
        logcat(LogPriority.INFO) { "RefreshLibraryCache: Starting full cache refresh" }
        mangaRepository.refreshLibraryCache()
        logcat(LogPriority.INFO) { "RefreshLibraryCache: Cache refresh complete" }
    }

    /**
     * Refresh the cache for a specific manga.
     * Use this after operations that modify a single manga's chapters/history.
     */
    suspend fun awaitForManga(mangaId: Long) {
        mangaRepository.refreshLibraryCacheForManga(mangaId)
    }

    /**
     * Check if the cache is valid (row counts match).
     * @return true if cache is valid, false if it needs refresh
     */
    suspend fun checkIntegrity(): Boolean {
        val (favoriteCount, cacheCount) = mangaRepository.checkLibraryCacheIntegrity()
        val isValid = favoriteCount == cacheCount
        if (!isValid) {
            logcat(LogPriority.WARN) { 
                "RefreshLibraryCache: Cache integrity check failed! " +
                "Favorites: $favoriteCount, Cache: $cacheCount" 
            }
        }
        return isValid
    }

    /**
     * Check integrity and refresh if needed.
     * Useful for startup checks.
     */
    suspend fun ensureIntegrity() {
        if (!checkIntegrity()) {
            logcat(LogPriority.INFO) { "RefreshLibraryCache: Cache invalid, triggering refresh" }
            await()
        } else {
            logcat(LogPriority.DEBUG) { "RefreshLibraryCache: Cache integrity verified" }
        }
    }
}
