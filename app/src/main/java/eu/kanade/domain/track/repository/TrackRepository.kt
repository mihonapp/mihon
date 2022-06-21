package eu.kanade.domain.track.repository

import eu.kanade.domain.track.model.Track

interface TrackRepository {

    suspend fun getTracksByMangaId(mangaId: Long): List<Track>

    suspend fun insertAll(tracks: List<Track>)
}
