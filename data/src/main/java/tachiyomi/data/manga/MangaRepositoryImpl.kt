package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibraryMangaForUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.DuplicateGroup
import tachiyomi.domain.manga.repository.DuplicatePair
import tachiyomi.domain.manga.repository.MangaRepository
import java.time.LocalDate
import java.time.ZoneId

class MangaRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return handler.awaitOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return handler.subscribeToOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return handler.awaitOneOrNull {
            mangasQueries.getMangaByUrlAndSource(
                url,
                sourceId,
                MangaMapper::mapManga,
            )
        }
    }

    override suspend fun getLiteMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return handler.awaitOneOrNull {
            mangasQueries.getLiteMangaByUrlAndSource(url, sourceId) { id, source, url, _, _, _, _, title, _, status, thumbnailUrl, favorite, last_update, next_update, _, _, _, cover_last_modified, date_added, _, _, _, _, _, _, notes ->
                MangaMapper.mapManga(
                    id = id,
                    source = source,
                    url = url,
                    artist = null,
                    author = null,
                    description = null,
                    genre = null,
                    title = title,
                    alternativeTitles = null,
                    status = status,
                    thumbnailUrl = thumbnailUrl,
                    favorite = favorite,
                    lastUpdate = last_update ?: 0,
                    nextUpdate = next_update ?: 0,
                    initialized = false,
                    viewerFlags = 0,
                    chapterFlags = 0,
                    coverLastModified = cover_last_modified,
                    dateAdded = date_added,
                    updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                    calculateInterval = 0,
                    lastModifiedAt = 0,
                    favoriteModifiedAt = null,
                    version = 0,
                    isSyncing = 0,
                    notes = notes,
                )
            }
        }
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return handler.subscribeToOneOrNull {
            mangasQueries.getMangaByUrlAndSource(
                url,
                sourceId,
                MangaMapper::mapManga,
            )
        }
    }

    override suspend fun getFavorites(): List<Manga> {
        return handler.awaitList { mangasQueries.getFavorites(MangaMapper::mapManga) }
    }

    override suspend fun getFavoritesEntry(): List<Manga> {
        return handler.awaitList {
            mangasQueries.getFavoritesEntry { id, source, url, title, artist, author, thumbnail_url, cover_last_modified, favorite ->
                MangaMapper.mapManga(
                    id = id,
                    source = source,
                    url = url,
                    artist = artist,
                    author = author,
                    description = null,
                    genre = null,
                    title = title,
                    alternativeTitles = null,
                    status = 0,
                    thumbnailUrl = thumbnail_url,
                    favorite = favorite,
                    lastUpdate = 0,
                    nextUpdate = 0,
                    initialized = false,
                    viewerFlags = 0,
                    chapterFlags = 0,
                    coverLastModified = cover_last_modified,
                    dateAdded = 0,
                    updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                    calculateInterval = 0,
                    lastModifiedAt = 0,
                    favoriteModifiedAt = null,
                    version = 0,
                    isSyncing = 0,
                    notes = "",
                )
            }
        }
    }

    override fun getFavoritesEntryBySourceId(sourceId: Long): Flow<List<Manga>> {
        return handler.subscribeToList {
            mangasQueries.getFavoritesEntryBySourceId(sourceId) { id, source, url, title, artist, author, thumbnail_url, cover_last_modified, favorite ->
                MangaMapper.mapManga(
                    id = id,
                    source = source,
                    url = url,
                    artist = artist,
                    author = author,
                    description = null,
                    genre = null,
                    title = title,
                    alternativeTitles = null,
                    status = 0,
                    thumbnailUrl = thumbnail_url,
                    favorite = favorite,
                    lastUpdate = 0,
                    nextUpdate = 0,
                    initialized = false,
                    viewerFlags = 0,
                    chapterFlags = 0,
                    coverLastModified = cover_last_modified,
                    dateAdded = 0,
                    updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                    calculateInterval = 0,
                    lastModifiedAt = 0,
                    favoriteModifiedAt = null,
                    version = 0,
                    isSyncing = 0,
                    notes = "",
                )
            }
        }
    }

    override suspend fun getFavoriteSourceAndUrl(): List<Pair<Long, String>> {
        val now = System.currentTimeMillis()
        if (cachedFavoriteSourceUrl != null && now - favoriteSourceUrlCacheTimestamp < FAVORITE_URL_CACHE_VALIDITY_MS) {
            return cachedFavoriteSourceUrl!!
        }
        
        val result = handler.awaitList { 
            mangasQueries.getFavoriteSourceAndUrl { source, url -> source to url }
        }
        
        cachedFavoriteSourceUrl = result
        favoriteSourceUrlCacheTimestamp = now
        return result
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return handler.awaitList { mangasQueries.getReadMangaNotInLibrary(MangaMapper::mapManga) }
    }

    // Cache library manga to avoid repeated expensive queries
    // Cache NEVER expires - only GetLibraryManga.refresh() should trigger new queries
    private var cachedLibraryManga: List<LibraryManga>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_VALIDITY_MS = Long.MAX_VALUE // Never expire - only refresh() triggers query
    
    // Cache favorite source/URL pairs for mass import performance
    private var cachedFavoriteSourceUrl: List<Pair<Long, String>>? = null
    private var favoriteSourceUrlCacheTimestamp: Long = 0
    private val FAVORITE_URL_CACHE_VALIDITY_MS = 10000L // 10 seconds
    
    private fun invalidateLibraryCache() {
        cachedLibraryManga = null
        cacheTimestamp = 0
    }
    
    private fun invalidateFavoriteUrlCache() {
        cachedFavoriteSourceUrl = null
        favoriteSourceUrlCacheTimestamp = 0
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        val caller = Thread.currentThread().stackTrace.take(15).joinToString("\\n  ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        val now = System.currentTimeMillis()
        if (cachedLibraryManga != null && now - cacheTimestamp < CACHE_VALIDITY_MS) {
            logcat(LogPriority.DEBUG) { "MangaRepositoryImpl.getLibraryManga: Using CACHE (age=${now - cacheTimestamp}ms, size=${cachedLibraryManga?.size})" }
            return cachedLibraryManga!!
        }
        
        logcat(LogPriority.WARN) { "MangaRepositoryImpl.getLibraryManga: Executing DB query (cache invalid/expired)\\nFull call stack:\\n  $caller" }
        val queryStart = System.currentTimeMillis()
        val result = handler.awaitList {
            libraryViewQueries.libraryGrid { id, source, url, _, _, _, genre, title, _, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, _, _, _, coverLastModified, dateAdded, _, _, _, _, _, _, notes, totalCount, readCount, latestUpload, chapterFetchedAt, lastRead, bookmarkCount, categories ->
                MangaMapper.mapLibraryManga(
                    id = id,
                    source = source,
                    url = url,
                    artist = null,
                    author = null,
                    description = null,
                    genre = genre,
                    title = title,
                    alternativeTitles = null,
                    status = status,
                    thumbnailUrl = thumbnailUrl,
                    favorite = favorite,
                    lastUpdate = lastUpdate,
                    nextUpdate = nextUpdate,
                    initialized = false,
                    viewerFlags = 0,
                    chapterFlags = 0,
                    coverLastModified = coverLastModified,
                    dateAdded = dateAdded,
                    updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                    calculateInterval = 0,
                    lastModifiedAt = 0,
                    favoriteModifiedAt = null,
                    version = 0,
                    isSyncing = 0,
                    notes = notes,
                    totalCount = totalCount,
                    readCount = readCount,
                    latestUpload = latestUpload,
                    chapterFetchedAt = chapterFetchedAt,
                    lastRead = lastRead,
                    bookmarkCount = bookmarkCount,
                    categories = categories,
                )
            }
        }
        
        val queryDuration = System.currentTimeMillis() - queryStart
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getLibraryManga: Query completed in ${queryDuration}ms, returned ${result.size} items" }
        
        // Update cache
        cachedLibraryManga = result
        cacheTimestamp = now
        return result
    }

    override suspend fun getLibraryMangaForUpdate(): List<LibraryMangaForUpdate> {
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getLibraryMangaForUpdate: Executing lightweight query" }
        val queryStart = System.currentTimeMillis()
        val result = handler.awaitList {
            libraryViewQueries.libraryForUpdate { id, source, url, title, status, favorite, lastUpdate, nextUpdate, updateStrategy, totalCount, readCount, categories ->
                MangaMapper.mapLibraryMangaForUpdate(
                    id = id,
                    source = source,
                    url = url,
                    title = title,
                    status = status,
                    favorite = favorite,
                    lastUpdate = lastUpdate,
                    nextUpdate = nextUpdate,
                    updateStrategy = updateStrategy,
                    totalCount = totalCount,
                    readCount = readCount,
                    categories = categories,
                )
            }
        }
        val queryDuration = System.currentTimeMillis() - queryStart
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getLibraryMangaForUpdate: Query completed in ${queryDuration}ms, returned ${result.size} items" }
        return result
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getLibraryMangaAsFlow: Creating new Flow subscription" }
        return handler.subscribeToList {
            logcat(LogPriority.INFO) { "MangaRepositoryImpl.getLibraryMangaAsFlow: Executing libraryGrid query" }
            libraryViewQueries.libraryGrid { id, source, url, _, _, _, genre, title, _, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, _, _, _, coverLastModified, dateAdded, _, _, _, _, _, _, notes, totalCount, readCount, latestUpload, chapterFetchedAt, lastRead, bookmarkCount, categories ->
                MangaMapper.mapLibraryManga(
                    id = id,
                    source = source,
                    url = url,
                    artist = null,
                    author = null,
                    description = null,
                    genre = genre,
                    title = title,
                    alternativeTitles = null,
                    status = status,
                    thumbnailUrl = thumbnailUrl,
                    favorite = favorite,
                    lastUpdate = lastUpdate,
                    nextUpdate = nextUpdate,
                    initialized = false,
                    viewerFlags = 0,
                    chapterFlags = 0,
                    coverLastModified = coverLastModified,
                    dateAdded = dateAdded,
                    updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
                    calculateInterval = 0,
                    lastModifiedAt = 0,
                    favoriteModifiedAt = null,
                    version = 0,
                    isSyncing = 0,
                    notes = notes,
                    totalCount = totalCount,
                    readCount = readCount,
                    latestUpload = latestUpload,
                    chapterFetchedAt = chapterFetchedAt,
                    lastRead = lastRead,
                    bookmarkCount = bookmarkCount,
                    categories = categories,
                )
            }
        }
            // Log when debounce passes through
            .onEach { list ->
                logcat(LogPriority.INFO) { "MangaRepositoryImpl.getLibraryMangaAsFlow: Flow emitting ${list.size} items (after debounce)" }
            }
            // Debounce to prevent rapid re-queries when multiple table changes occur
            // (e.g., adding manga triggers changes to mangas, chapters, mangas_categories)
            // 500ms allows batched operations to complete before triggering query
            .debounce(500)
            // Skip emissions if the resulting list hasn't changed
            .distinctUntilChanged()
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mangasQueries.getFavoriteBySourceId(sourceId, MangaMapper::mapManga) }
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount> {
        return handler.awaitList {
            mangasQueries.getDuplicateLibraryManga(id, title, MangaMapper::mapMangaWithChapterCount)
        }
    }

    override suspend fun findDuplicatesExact(): List<DuplicateGroup> {
        return handler.awaitList {
            mangasQueries.findDuplicatesExact { normalizedTitle, ids, count ->
                DuplicateGroup(
                    normalizedTitle = normalizedTitle ?: "",
                    ids = ids?.let { idString -> idString.split(",").mapNotNull { id -> id.toLongOrNull() } } ?: emptyList(),
                    count = count.toInt(),
                )
            }
        }
    }

    override suspend fun findDuplicatesContains(): List<DuplicatePair> {
        return handler.awaitList {
            mangasQueries.findDuplicatesContains { idA, titleA, idB, titleB ->
                DuplicatePair(
                    idA = idA,
                    titleA = titleA,
                    idB = idB,
                    titleB = titleB,
                )
            }
        }
    }

    override suspend fun getFavoriteGenres(): List<Pair<Long, List<String>?>> {
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getFavoriteGenres: Executing lightweight genres query" }
        val queryStart = System.currentTimeMillis()
        val result = handler.awaitList {
            mangasQueries.getFavoriteGenres { id, genre ->
                // genre is already List<String>? via StringListColumnAdapter
                id to genre
            }
        }
        val queryDuration = System.currentTimeMillis() - queryStart
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getFavoriteGenres: Query completed in ${queryDuration}ms, returned ${result.size} items" }
        return result
    }

    override suspend fun getFavoriteSourceUrlPairs(): List<Pair<Long, String>> {
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getFavoriteSourceUrlPairs: Executing ultra-lightweight source+url query" }
        val queryStart = System.currentTimeMillis()
        val result = handler.awaitList {
            mangasQueries.getFavoriteSourceAndUrlPairs { source, url ->
                source to url
            }
        }
        val queryDuration = System.currentTimeMillis() - queryStart
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getFavoriteSourceUrlPairs: Query completed in ${queryDuration}ms, returned ${result.size} items" }
        return result
    }

    override suspend fun getFavoriteSourceIds(): List<Long> {
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getFavoriteSourceIds: Executing ultra-lightweight source IDs query" }
        val queryStart = System.currentTimeMillis()
        val result = handler.awaitList {
            mangasQueries.getFavoriteSourceIds()
        }
        val queryDuration = System.currentTimeMillis() - queryStart
        logcat(LogPriority.INFO) { "MangaRepositoryImpl.getFavoriteSourceIds: Query completed in ${queryDuration}ms, returned ${result.size} sources" }
        return result
    }

    override suspend fun getMangaWithCounts(ids: List<Long>): List<MangaWithChapterCount> {
        if (ids.isEmpty()) return emptyList()
        return handler.awaitList {
            mangasQueries.getMangaWithCounts(ids, MangaMapper::mapMangaWithChapterCount)
        }
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return handler.subscribeToList {
            mangasQueries.getUpcomingManga(epochMillis, statuses, MangaMapper::mapManga)
        }
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            handler.await { mangasQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.map { categoryId ->
                mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
        invalidateLibraryCache()
    }

    override suspend fun setMangasCategories(mangaIds: List<Long>, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangaIds.forEach { mangaId ->
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
                categoryIds.forEach { categoryId ->
                    mangas_categoriesQueries.insert(mangaId, categoryId)
                }
            }
        }
    }

    override suspend fun addMangasCategories(mangaIds: List<Long>, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangaIds.forEach { mangaId ->
                categoryIds.forEach { categoryId ->
                    try {
                        mangas_categoriesQueries.insert(mangaId, categoryId)
                    } catch (e: Exception) {
                        // Ignore duplicates
                    }
                }
            }
        }
    }

    override suspend fun removeMangasCategories(mangaIds: List<Long>, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangaIds.forEach { mangaId ->
                categoryIds.forEach { categoryId ->
                    mangas_categoriesQueries.deleteMangaCategory(mangaId, categoryId)
                }
            }
        }
    }

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdate(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertNetworkManga(manga: List<Manga>): List<Manga> {
        return handler.await(inTransaction = true) {
            manga.map {
                mangasQueries.insertNetworkManga(
                    source = it.source,
                    url = it.url,
                    artist = it.artist,
                    author = it.author,
                    description = it.description,
                    genre = it.genre,
                    title = it.title,
                    alternativeTitles = it.alternativeTitles,
                    status = it.status,
                    thumbnailUrl = it.thumbnailUrl,
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    chapterFlags = it.chapterFlags,
                    coverLastModified = it.coverLastModified,
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy,
                    version = it.version,
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = MangaMapper::mapManga,
                )
                    .executeAsOne()
            }
        }
    }

    private suspend fun partialUpdate(vararg mangaUpdates: MangaUpdate) {
        handler.await(inTransaction = true) {
            mangaUpdates.forEach { value ->
                mangasQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    alternativeTitles = value.alternativeTitles?.let(StringListColumnAdapter::encode),
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy?.let(UpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                    notes = value.notes,
                )
            }
        }
        // Invalidate if favorite status or other library-relevant fields changed
        if (mangaUpdates.any { it.favorite != null }) {
            invalidateLibraryCache()
            invalidateFavoriteUrlCache()
        }
    }

    override suspend fun normalizeAllUrls(): Int {
        return try {
            var count = 0
            val seen = mutableSetOf<Pair<Long, String>>()
            handler.await(inTransaction = true) {
                val allManga = mangasQueries.getAllManga(MangaMapper::mapManga).executeAsList()
                allManga.forEach { manga ->
                    val normalizedUrl = manga.url.trimEnd('/').substringBefore('#')
                    if (normalizedUrl != manga.url) {
                        val key = manga.source to normalizedUrl
                        if (key in seen) {
                            logcat(LogPriority.WARN) { "Skipping duplicate: ${manga.title} (${manga.url}) would conflict with existing normalized URL" }
                        } else {
                            mangasQueries.update(
                                source = null,
                                url = normalizedUrl,
                                artist = null,
                                author = null,
                                description = null,
                                genre = null,
                                title = null,
                                alternativeTitles = null,
                                status = null,
                                thumbnailUrl = null,
                                favorite = null,
                                lastUpdate = null,
                                nextUpdate = null,
                                calculateInterval = null,
                                initialized = null,
                                viewer = null,
                                chapterFlags = null,
                                coverLastModified = null,
                                dateAdded = null,
                                mangaId = manga.id,
                                updateStrategy = null,
                                version = null,
                                isSyncing = null,
                                notes = null,
                            )
                            seen.add(key)
                            count++
                        }
                    } else {
                        seen.add(manga.source to manga.url)
                    }
                }
            }
            count
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to normalize URLs" }
            0
        }
    }
}
