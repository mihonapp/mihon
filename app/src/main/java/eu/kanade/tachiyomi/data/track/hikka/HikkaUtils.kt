package eu.kanade.tachiyomi.data.track.hikka

import eu.kanade.tachiyomi.data.database.models.Track
import java.security.MessageDigest

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
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray())

    return hash.copyOfRange(0, 8).fold(0L) { acc, byte ->
        acc shl 8 or (byte.toLong() and 0xff)
    }
}
