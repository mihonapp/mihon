package tachiyomi.data.track

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class TrackRepositoryImpl(
    private val handler: DatabaseHandler,
) : TrackRepository {

    override suspend fun getTrackById(id: Long): Track? {
        return handler.awaitOneOrNull { manga_syncQueries.getTrackById(id, ::mapTrack) }
    }

    override suspend fun getTracksByMangaId(mangaId: Long): List<Track> {
        return handler.awaitList {
            manga_syncQueries.getTracksByMangaId(mangaId, ::mapTrack)
        }
    }

    override fun getTracksAsFlow(): Flow<List<Track>> {
        return handler.subscribeToList {
            manga_syncQueries.getTracks(::mapTrack)
        }
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>> {
        return handler.subscribeToList {
            manga_syncQueries.getTracksByMangaId(mangaId, ::mapTrack)
        }
    }

    override suspend fun delete(mangaId: Long, syncId: Long) {
        handler.await {
            manga_syncQueries.delete(
                mangaId = mangaId,
                syncId = syncId,
            )
        }
    }

    override suspend fun insert(track: Track) {
        insertValues(track)
    }

    override suspend fun insertAll(tracks: List<Track>) {
        insertValues(*tracks.toTypedArray())
    }

    private suspend fun insertValues(vararg tracks: Track) {
        handler.await(inTransaction = true) {
            tracks.forEach { mangaTrack ->
                manga_syncQueries.insert(
                    mangaId = mangaTrack.mangaId,
                    syncId = mangaTrack.syncId,
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
                )
            }
        }
    }

    private fun mapTrack(
        id: Long,
        mangaId: Long,
        syncId: Long,
        remoteId: Long,
        libraryId: Long?,
        title: String,
        lastChapterRead: Double,
        totalChapters: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
    ): Track = Track(
        id = id,
        mangaId = mangaId,
        syncId = syncId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
    )
}
