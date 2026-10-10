package tachiyomi.data.updates

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaUpdateErrorRepositoryImpl(
    private val database: Database,
) : MangaUpdateErrorRepository {

    override fun subscribeCount(): Flow<Long> {
        return database.manga_update_errorQueries
            .getCount()
            .subscribeToOne()
    }

    override fun subscribeWithManga(): Flow<List<MangaUpdateErrorWithManga>> {
        return database.manga_update_errorQueries
            .getAllWithManga(::mapMangaUpdateErrorWithManga)
            .subscribeToList()
    }

    override suspend fun insert(mangaId: Long, errorMessage: String?, timestamp: Long) {
        database.manga_update_errorQueries.insert(
            errorMessage = errorMessage,
            timestamp = timestamp,
            mangaId = mangaId,
        )
    }

    override suspend fun delete(mangaId: Long) {
        database.manga_update_errorQueries.delete(mangaId)
    }

    override suspend fun deleteByMangaIds(mangaIds: List<Long>) {
        database.manga_update_errorQueries.deleteByMangaIds(mangaIds)
    }

    override suspend fun deleteAll() {
        database.manga_update_errorQueries.deleteAll()
    }

    @Suppress("LongParameterList")
    private fun mapMangaUpdateErrorWithManga(
        mangaId: Long,
        errorMessage: String?,
        timestamp: Long,
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: UpdateStrategy,
        remoteMemo: JsonObject,
        userFavoriteAt: Long?,
        userNotes: String,
        userReaderFlags: Long,
        userChapterFlags: Long,
        stateChapterLastUpdate: Long?,
        stateChapterNextUpdate: Long?,
        stateChapterFetchInterval: Long,
        stateCoverLastModified: Long,
        stateInitialized: Boolean,
    ): MangaUpdateErrorWithManga = MangaUpdateErrorWithManga(
        error = MangaUpdateError(mangaId, errorMessage, timestamp),
        manga = MangaMapper.mapManga(
            id = id,
            sourceId = sourceId,
            remoteUrl = remoteUrl,
            remoteTitle = remoteTitle,
            remoteAuthor = remoteAuthor,
            remoteArtist = remoteArtist,
            remoteDescription = remoteDescription,
            remoteGenre = remoteGenre,
            remoteStatus = remoteStatus,
            remoteCover = remoteCover,
            remoteUpdateStrategy = remoteUpdateStrategy,
            remoteMemo = remoteMemo,
            userFavoriteAt = userFavoriteAt,
            userNotes = userNotes,
            userReaderFlags = userReaderFlags,
            userChapterFlags = userChapterFlags,
            stateChapterLastUpdate = stateChapterLastUpdate,
            stateChapterNextUpdate = stateChapterNextUpdate,
            stateChapterFetchInterval = stateChapterFetchInterval,
            stateCoverLastModified = stateCoverLastModified,
            stateInitialized = stateInitialized,
        ),
    )
}
