package tachiyomi.data.updates

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.Database
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.domain.updates.model.MangaUpdateError
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga
import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

class MangaUpdateErrorRepositoryImpl(
    private val database: Database,
) : MangaUpdateErrorRepository {

    override suspend fun getAll(): List<MangaUpdateError> {
        return database.manga_update_errorsQueries
            .getAll(::mapMangaUpdateError)
            .awaitAsList()
    }

    override suspend fun getByMangaId(mangaId: Long): MangaUpdateError? {
        return database.manga_update_errorsQueries
            .getByMangaId(mangaId, ::mapMangaUpdateError)
            .awaitAsOneOrNull()
    }

    override fun subscribeAll(): Flow<List<MangaUpdateError>> {
        return database.manga_update_errorsQueries
            .getAll(::mapMangaUpdateError)
            .subscribeToList()
    }

    override fun subscribeCount(): Flow<Long> {
        return database.manga_update_errorsQueries
            .getCount()
            .subscribeToOne()
    }

    override fun subscribeWithManga(): Flow<List<MangaUpdateErrorWithManga>> {
        return database.manga_update_errorsQueries
            .getAllWithManga(::mapMangaUpdateErrorWithManga)
            .subscribeToList()
    }

    override suspend fun getCount(): Long {
        return database.manga_update_errorsQueries
            .getCount()
            .awaitAsOne()
    }

    override suspend fun insert(mangaId: Long, errorMessage: String?, timestamp: Long) {
        database.manga_update_errorsQueries.insert(mangaId, errorMessage, timestamp)
    }

    override suspend fun delete(mangaId: Long) {
        database.manga_update_errorsQueries.delete(mangaId)
    }

    override suspend fun deleteAll() {
        database.manga_update_errorsQueries.deleteAll()
    }

    override suspend fun deleteNonFavorites() {
        database.manga_update_errorsQueries.deleteNonFavorites()
    }

    private fun mapMangaUpdateError(
        mangaId: Long,
        errorMessage: String?,
        timestamp: Long,
    ): MangaUpdateError = MangaUpdateError(
        mangaId = mangaId,
        errorMessage = errorMessage,
        timestamp = timestamp,
    )

    @Suppress("LongParameterList")
    private fun mapMangaUpdateErrorWithManga(
        mangaId: Long,
        errorMessage: String?,
        timestamp: Long,
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        memo: JsonObject,
    ): MangaUpdateErrorWithManga = MangaUpdateErrorWithManga(
        error = mapMangaUpdateError(mangaId, errorMessage, timestamp),
        manga = MangaMapper.mapManga(
            id = id,
            source = source,
            url = url,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            title = title,
            status = status,
            thumbnailUrl = thumbnailUrl,
            favorite = favorite,
            lastUpdate = lastUpdate,
            nextUpdate = nextUpdate,
            initialized = initialized,
            viewerFlags = viewerFlags,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            dateAdded = dateAdded,
            updateStrategy = updateStrategy,
            calculateInterval = calculateInterval,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
            version = version,
            isSyncing = isSyncing,
            notes = notes,
            memo = memo,
        ),
    )
}
