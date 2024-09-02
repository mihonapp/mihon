package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

fun Track.toApiStatus() = when (status) {
    Anilist.READING -> "CURRENT"
    Anilist.COMPLETED -> "COMPLETED"
    Anilist.ON_HOLD -> "PAUSED"
    Anilist.DROPPED -> "DROPPED"
    Anilist.PLAN_TO_READ -> "PLANNING"
    Anilist.REREADING -> "REPEATING"
    else -> throw NotImplementedError("Unknown status: $status")
}

private val preferences: TrackPreferences by injectLazy()

fun DomainTrack.toApiScore(): String = when (preferences.anilistScoreType().get()) {
    // 10 point
    "POINT_10" -> (score.toInt() / 10).toString()
    // 100 point
    "POINT_100" -> score.toInt().toString()
    // 5 stars
    "POINT_5" -> when {
        score == 0.0 -> "0"
        score < 30 -> "1"
        score < 50 -> "2"
        score < 70 -> "3"
        score < 90 -> "4"
        else -> "5"
    }
    // Smiley
    "POINT_3" -> when {
        score == 0.0 -> "0"
        score <= 35 -> ":("
        score <= 60 -> ":|"
        else -> ":)"
    }
    // 10 point decimal
    "POINT_10_DECIMAL" -> (score / 10).toString()
    else -> throw NotImplementedError("Unknown score type")
}
