package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibraryMangaForUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount

data class DuplicateGroup(
    val normalizedTitle: String,
    val ids: List<Long>,
    val count: Int,
)

data class DuplicatePair(
    val idA: Long,
    val titleA: String,
    val idB: Long,
    val titleB: String,
)

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    suspend fun getLiteMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>

    suspend fun getFavorites(): List<Manga>

    suspend fun getFavoritesEntry(): List<Manga>

    fun getFavoritesEntryBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getFavoriteSourceAndUrl(): List<Pair<Long, String>>

    suspend fun getReadMangaNotInLibrary(): List<Manga>

    suspend fun getLibraryManga(): List<LibraryManga>

    suspend fun getLibraryMangaForUpdate(): List<LibraryMangaForUpdate>

    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount>

    suspend fun findDuplicatesExact(): List<DuplicateGroup>

    suspend fun findDuplicatesContains(): List<DuplicatePair>

    /**
     * Find duplicates by URL within the same source.
     * Returns groups where multiple manga have the same URL from the same source.
     */
    suspend fun findDuplicatesByUrl(): List<DuplicateGroup>

    /**
     * Get lightweight favorite genres for tag counting.
     * Returns list of (mangaId, genreList) pairs - much faster than getLibraryManga().
     */
    suspend fun getFavoriteGenres(): List<Pair<Long, List<String>?>>

    /**
     * Get lightweight favorite genres with source ID for tag counting filtered by content type.
     * Returns list of (mangaId, sourceId, genreList) triples.
     */
    suspend fun getFavoriteGenresWithSource(): List<Triple<Long, Long, List<String>?>>

    /**
     * Get ultra-lightweight source + url pairs for duplicate checking.
     * Much faster than getLibraryMangaForUpdate() - avoids libraryView JOIN entirely.
     */
    suspend fun getFavoriteSourceUrlPairs(): List<Pair<Long, String>>

    /**
     * Get just the distinct source IDs from favorites - ultra-lightweight for extension listing.
     * Avoids expensive libraryView JOIN entirely.
     */
    suspend fun getFavoriteSourceIds(): List<Long>

    suspend fun getMangaWithCounts(ids: List<Long>): List<MangaWithChapterCount>

    suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun setMangasCategories(mangaIds: List<Long>, categoryIds: List<Long>)

    suspend fun addMangasCategories(mangaIds: List<Long>, categoryIds: List<Long>)

    suspend fun removeMangasCategories(mangaIds: List<Long>, categoryIds: List<Long>)

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean

    suspend fun insertNetworkManga(manga: List<Manga>): List<Manga>

    suspend fun normalizeAllUrls(): Int
    
    /**
     * Data class to hold information about a duplicate URL entry.
     */
    data class DuplicateUrlInfo(
        val mangaId: Long,
        val title: String,
        val oldUrl: String,
        val normalizedUrl: String,
    )
    
    /**
     * Normalize URLs with advanced options.
     * @param removeDoubleSlashes whether to also remove double slashes from URLs
     * @return Pair of (count of normalized URLs, list of skipped duplicates)
     */
    suspend fun normalizeAllUrlsAdvanced(removeDoubleSlashes: Boolean): Pair<Int, List<DuplicateUrlInfo>>

    /**
     * Remove (unfavorite) manga that would become duplicates after URL normalization.
     * This allows the user to clean up duplicates before running normalization.
     * @param removeDoubleSlashes whether to also consider double slashes when detecting duplicates
     * @return Pair of (count of removed duplicates, list of removed items with Triple(title, url, normalizedUrl))
     */
    suspend fun removePotentialDuplicates(removeDoubleSlashes: Boolean): Pair<Int, List<Triple<String, String, String>>>

    /**
     * Refresh the library cache table.
     * Call this after bulk operations or on app startup to ensure cache integrity.
     */
    suspend fun refreshLibraryCache()

    /**
     * Refresh the library cache for a specific manga.
     * Useful after individual manga operations.
     */
    suspend fun refreshLibraryCacheForManga(mangaId: Long)

    /**
     * Invalidate the in-memory library cache.
     * Forces the next getLibraryManga() call to re-query the database.
     * Use this before forced refreshes to ensure fresh data.
     */
    fun invalidateLibraryCache()

    /**
     * Normalize all tags/genres in the library.
     * - Trims whitespace
     * - Removes duplicates (case-insensitive)
     * - Removes empty tags
     * @return count of manga with normalized tags
     */
    suspend fun normalizeAllTags(): Int

    /**
     * Check library cache integrity.
     * @return Pair of (favoriteCount, cacheCount) - should be equal if cache is valid
     */
    suspend fun checkLibraryCacheIntegrity(): Pair<Long, Long>
}
