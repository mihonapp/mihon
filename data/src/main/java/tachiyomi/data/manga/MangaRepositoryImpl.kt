package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaRepositoryImpl(
    private val database: Database,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return database.mangaQueries
            .getMangaById(id, MangaMapper::mapManga)
            .awaitAsOne()
    }

    override fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return database.mangaQueries
            .getMangaById(id, MangaMapper::mapManga)
            .subscribeToOne()
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return database.mangaQueries
            .getMangaByUrlAndSource(sourceId = sourceId, remoteUrl = url, mapper = MangaMapper::mapManga)
            .awaitAsOneOrNull()
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return database.mangaQueries
            .getMangaByUrlAndSource(sourceId = sourceId, remoteUrl = url, mapper = MangaMapper::mapManga)
            .subscribeToOneOrNull()
    }

    override suspend fun getFavorites(): List<Manga> {
        return database.mangaQueries
            .getFavorites(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return database.mangaQueries
            .getReadMangaNotInLibrary(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        return database.libraryViewQueries
            .library(MangaMapper::mapLibraryManga)
            .awaitAsList()
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return database.libraryViewQueries
            .library(MangaMapper::mapLibraryManga)
            .subscribeToList()
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return database.mangaQueries
            .getFavoriteBySourceId(sourceId, MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount> {
        return database.mangaQueries
            .getDuplicateLibraryManga(id, title, MangaMapper::mapMangaWithChapterCount)
            .awaitAsList()
    }

    override suspend fun getUpcomingManga(
        statuses: Set<Long>,
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Manga>> {
        val timeZone = TimeZone.currentSystemDefault()
        val epochMillis =
            Clock.System.now().toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        return database.mangaQueries
            .getUpcomingManga(
                startOfDay = epochMillis,
                statuses = statuses,
                includedEmpty = includedCategories.isEmpty(),
                includedCategories = includedCategories,
                excludedEmpty = excludedCategories.isEmpty(),
                excludedCategories = excludedCategories,
                mapper = MangaMapper::mapManga,
            )
            .subscribeToList()
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            database.mangaQueries.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Boolean) {
        database.mangaQueries.deleteNonLibraryManga(sourceIds, keepReadManga.toLong())
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.manga_categoryQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.forEach { categoryId ->
                database.manga_categoryQueries.insert(mangaId = mangaId, categoryId = categoryId)
            }
        }
    }

    override suspend fun getExcludedScanlators(mangaId: Long): Set<String> {
        return database.excluded_scanlatorQueries
            .getExcludedScanlatorsByMangaId(mangaId)
            .awaitAsList()
            .toSet()
    }

    override suspend fun getExcludedScanlators(mangaIds: List<Long>): Map<Long, List<String>> {
        return database.excluded_scanlatorQueries
            .getExcludedScanlatorsByMangaIds(mangaIds)
            .awaitAsList()
            .groupBy({ it.manga_id }, { it.scanlator })
    }

    override fun getExcludedScanlatorsAsFlow(mangaId: Long): Flow<Set<String>> {
        return database.excluded_scanlatorQueries
            .getExcludedScanlatorsByMangaId(mangaId)
            .subscribeToList()
            .map { it.toSet() }
    }

    override suspend fun setExcludedScanlators(mangaId: Long, excludedScanlators: Set<String>) {
        database.transaction {
            val current = database.excluded_scanlatorQueries
                .getExcludedScanlatorsByMangaId(mangaId)
                .awaitAsList()
                .toSet()
            excludedScanlators.minus(current).forEach { scanlator ->
                database.excluded_scanlatorQueries.insert(mangaId, scanlator)
            }
            val toRemove = current.minus(excludedScanlators)
            // An empty delete still tells every chapter query to run again
            if (toRemove.isNotEmpty()) {
                database.excluded_scanlatorQueries.remove(mangaId, toRemove)
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
        return database.transactionWithResult {
            manga.map {
                database.mangaQueries.insertNetworkManga(
                    sourceId = it.source,
                    remoteUrl = it.url,
                    remoteArtist = it.artist,
                    remoteAuthor = it.author,
                    remoteDescription = it.description,
                    remoteGenre = it.genre,
                    remoteTitle = it.title,
                    remoteStatus = it.status,
                    remoteCover = it.thumbnailUrl,
                    userFavoriteAt = it.favoriteAt,
                    stateChapterLastUpdate = it.lastUpdate,
                    stateChapterNextUpdate = it.nextUpdate,
                    stateChapterFetchInterval = it.fetchInterval.toLong(),
                    stateInitialized = it.initialized,
                    userReaderFlags = it.viewerFlags,
                    userChapterFlags = it.chapterFlags,
                    stateCoverLastModified = it.coverLastModified,
                    remoteUpdateStrategy = it.updateStrategy,
                    remoteMemo = it.memo,
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = MangaMapper::mapManga,
                )
                    .awaitAsOne()
            }
        }
    }

    override suspend fun updateRemote(update: MangaRemoteUpdate): Boolean {
        return try {
            database.mangaQueries.updateRemote(
                remoteArtist = update.artist,
                remoteAuthor = update.author,
                remoteDescription = update.description,
                remoteGenre = update.genre,
                remoteTitle = update.title,
                remoteStatus = update.status,
                remoteCover = update.thumbnailUrl,
                stateInitialized = update.initialized,
                stateCoverLastModified = update.coverLastModified,
                remoteUpdateStrategy = update.updateStrategy,
                remoteMemo = update.memo,
                id = update.id,
            )
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg mangaUpdates: MangaUpdate) {
        database.transaction {
            mangaUpdates.forEach { value ->
                with(value) {
                    database.mangaQueries.update(
                        userFavoriteAtSet = isSet(::favoriteAt),
                        userFavoriteAt = favoriteAt,
                        stateChapterLastUpdate = lastUpdate,
                        stateChapterNextUpdate = nextUpdate,
                        stateChapterFetchInterval = fetchInterval?.toLong(),
                        userReaderFlags = viewerFlags,
                        userChapterFlags = chapterFlags,
                        stateCoverLastModified = coverLastModified,
                        id = id,
                        userNotes = notes,
                    )
                }
            }
        }
    }
}
