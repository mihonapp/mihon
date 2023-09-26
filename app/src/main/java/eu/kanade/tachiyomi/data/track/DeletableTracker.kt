package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.Track

/**
 * Tracker that support deleting am entry from a user's list.
 */
interface DeletableTracker {

    suspend fun delete(track: Track): Track
}
