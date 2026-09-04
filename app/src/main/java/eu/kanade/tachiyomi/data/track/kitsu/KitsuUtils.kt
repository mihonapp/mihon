package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toKitsuApiStatus() = when (status) {
    Kitsu.READING -> "CURRENT"
    Kitsu.COMPLETED -> "COMPLETED"
    Kitsu.ON_HOLD -> "ON_HOLD"
    Kitsu.DROPPED -> "DROPPED"
    Kitsu.PLAN_TO_READ -> "PLANNED"
    else -> throw Exception("Unknown status: $status")
}

fun String.toKitsuLocalStatus() = when (this) {
    "CURRENT" -> Kitsu.READING
    "COMPLETED" -> Kitsu.COMPLETED
    "ON_HOLD" -> Kitsu.ON_HOLD
    "DROPPED" -> Kitsu.DROPPED
    "PLANNED" -> Kitsu.PLAN_TO_READ
    else -> throw Exception("Unknown status: $this")
}
