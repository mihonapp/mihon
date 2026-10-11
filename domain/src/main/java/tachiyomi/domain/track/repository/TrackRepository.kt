package tachiyomi.domain.track.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.model.Track

interface TrackRepository {

    suspend fun getTrackById(id: Long): Track?

    suspend fun getTracksByMangaId(mangaId: Long): List<Track>

    suspend fun getTracks(): List<Track>

    fun getTracksAsFlow(): Flow<List<Track>>

    fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>>

    suspend fun delete(mangaId: Long, trackerId: Long)

    suspend fun upsert(track: Track)

    suspend fun upsertAll(tracks: List<Track>)
}
