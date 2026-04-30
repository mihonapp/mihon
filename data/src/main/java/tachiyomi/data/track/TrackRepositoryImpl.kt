package tachiyomi.data.track

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class TrackRepositoryImpl(
    private val database: Database,
) : TrackRepository {

    override suspend fun getTrackById(id: Long): Track? {
        return database.manga_syncQueries
            .getTrackById(id, TrackMapper::mapTrack)
            .awaitAsOneOrNull()
    }

    override suspend fun getTracksByMangaId(mangaId: Long): List<Track> {
        return database.manga_syncQueries
            .getTracksByMangaId(mangaId, TrackMapper::mapTrack)
            .awaitAsList()
    }

    override fun getTracksAsFlow(): Flow<List<Track>> {
        return database.manga_syncQueries
            .getTracks(TrackMapper::mapTrack)
            .subscribeToList()
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>> {
        return database.manga_syncQueries
            .getTracksByMangaId(mangaId, TrackMapper::mapTrack)
            .subscribeToList()
    }

    override suspend fun delete(mangaId: Long, trackerId: Long) {
        database.manga_syncQueries.delete(
            mangaId = mangaId,
            syncId = trackerId,
        )
    }

    override suspend fun insert(track: Track) {
        insertValues(track)
    }

    override suspend fun insertAll(tracks: List<Track>) {
        insertValues(*tracks.toTypedArray())
    }

    private suspend fun insertValues(vararg tracks: Track) {
        database.transaction {
            tracks.forEach { mangaTrack ->
                database.manga_syncQueries.insert(
                    mangaId = mangaTrack.mangaId,
                    syncId = mangaTrack.trackerId,
                    remoteId = mangaTrack.remoteId,
                    libraryId = mangaTrack.libraryId,
                    title = mangaTrack.title,
                    lastChapterRead = mangaTrack.lastChapterRead,
                    totalChapters = mangaTrack.totalChapters,
                    status = mangaTrack.status,
                    score = mangaTrack.score,
                    remoteUrl = mangaTrack.remoteUrl,
                    startDate = mangaTrack.startDate,
                    finishDate = mangaTrack.finishDate,
                    private = mangaTrack.private,
                )
            }
        }
    }
}
