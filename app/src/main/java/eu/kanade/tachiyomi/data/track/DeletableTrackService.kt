package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.Track

/**
 * For track services api that support deleting a manga entry for a user's list
 */
interface DeletableTrackService {

    suspend fun delete(track: Track): Track
}
