package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toApiStatus() = when (status) {
    Bangumi.READING -> "do"
    Bangumi.COMPLETED -> "collect"
    Bangumi.ON_HOLD -> "on_hold"
    Bangumi.DROPPED -> "dropped"
    Bangumi.PLAN_TO_READ -> "wish"
    else -> throw NotImplementedError("Unknown status: $status")
}
