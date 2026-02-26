package eu.kanade.tachiyomi.data.track.hikka

import eu.kanade.tachiyomi.data.database.models.Track
import java.util.UUID

fun Track.toApiStatus() = when (status) {
    Hikka.READING -> "reading"
    Hikka.COMPLETED -> "completed"
    Hikka.ON_HOLD -> "on_hold"
    Hikka.DROPPED -> "dropped"
    Hikka.PLAN_TO_READ -> "planned"
    Hikka.REREADING -> "reading"
    else -> throw NotImplementedError("Hikka: Unknown status: $status")
}

fun toTrackStatus(status: String) = when (status) {
    "reading" -> Hikka.READING
    "completed" -> Hikka.COMPLETED
    "on_hold" -> Hikka.ON_HOLD
    "dropped" -> Hikka.DROPPED
    "planned" -> Hikka.PLAN_TO_READ
    else -> throw NotImplementedError("Hikka: Unknown status: $status")
}

fun stringToNumber(input: String): Long {
    val uuid = UUID.nameUUIDFromBytes(input.toByteArray())
    return uuid.mostSignificantBits and Long.MAX_VALUE
}
