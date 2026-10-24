package eu.kanade.tachiyomi.data.track.mangadex

import java.util.UUID

// Deterministic encoding of MangaDex UUIDs into a Long for internal storage.
// The full UUID is preserved in [eu.kanade.tachiyomi.data.database.models.Track.tracking_url]
// and is the authoritative id used for API calls.
internal fun String.asUuidToLong(): Long = UUID.fromString(this).mostSignificantBits

internal fun String.getUuidFromTrackingUrl(): String {
    require(this.startsWith("https://mangadex.org/title/")) { "Not a MangaDex tracking URL: $this" }
    return removePrefix("https://mangadex.org/title/")
        .substringBefore("/")
        .also { UUID.fromString(it) }
}

internal fun String.getUuidFromTrackingUrlOrNull(): String? = try {
    getUuidFromTrackingUrl()
} catch (_: Exception) {
    null
}

internal fun Long.toMdStatus(): String = when (this) {
    MangaDex.READING -> "reading"
    MangaDex.COMPLETED -> "completed"
    MangaDex.ON_HOLD -> "on_hold"
    MangaDex.DROPPED -> "dropped"
    MangaDex.PLAN_TO_READ -> "plan_to_read"
    MangaDex.RE_READING -> "re_reading"
    else -> throw IllegalArgumentException("Unknown MangaDex status: $this")
}

internal fun String?.fromMdStatus(): Long = when (this) {
    "reading" -> MangaDex.READING
    "completed" -> MangaDex.COMPLETED
    "on_hold" -> MangaDex.ON_HOLD
    "dropped" -> MangaDex.DROPPED
    "plan_to_read" -> MangaDex.PLAN_TO_READ
    "re_reading" -> MangaDex.RE_READING
    else -> MangaDex.PLAN_TO_READ
}
