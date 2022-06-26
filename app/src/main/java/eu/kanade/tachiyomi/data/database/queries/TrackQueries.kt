package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.tables.TrackTable

interface TrackQueries : DbProvider {

    fun getTracks() = db.get()
        .listOfObjects(Track::class.java)
        .withQuery(
            Query.builder()
                .table(TrackTable.TABLE)
                .build(),
        )
        .prepare()

    fun insertTrack(track: Track) = db.put().`object`(track).prepare()
}
