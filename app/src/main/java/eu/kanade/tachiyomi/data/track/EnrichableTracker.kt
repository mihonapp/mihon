package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.flow.Flow

/**
 * Tracker whose [Tracker.search] returns lightweight results that can be enriched
 * asynchronously after the initial list is shown to the user. Implementations should
 * mutate the passed-in [TrackSearch] entries in place and emit each one as its
 * enrichment finishes.
 */
interface EnrichableTracker {

    fun enrichSearchResults(items: List<TrackSearch>): Flow<TrackSearch>
}
