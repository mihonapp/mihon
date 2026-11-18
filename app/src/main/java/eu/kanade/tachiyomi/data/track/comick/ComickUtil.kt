package eu.kanade.tachiyomi.data.track.comick

import java.util.Base64

// Deterministic en/decoding HID <-> Long for our internal storage
internal fun String.asHidToLong(): Long {
    require(this.length == 8) { "HID must be 8 characters for decoding, got ${this.length}: $this" }
    return Base64.getUrlDecoder()
        .decode(this)
        .fold(0) { acc, b ->
            (acc shl 8) or (b.toLong() and 0xFF)
        }
}

internal fun Long.toHid(): String {
    require(this in 0 until (1L shl 48)) { "Long not in 48-bit range for HID conversion: $this" }
    return Base64.getUrlEncoder()
        .encodeToString(
            ByteArray(6) {
                (this shr (8 * (5 - it))).toByte()
            },
        )
}

internal fun Long.toApiListStatus() = when (this) {
    Comick.READING -> 1
    Comick.COMPLETED -> 2
    Comick.ON_HOLD -> 3
    Comick.DROPPED -> 4
    Comick.PLAN_TO_READ -> 5
    else -> throw NotImplementedError("Unknown status: $this")
}

internal fun Long.fromApiListStatus() = when (this) {
    1L -> Comick.READING
    2L -> Comick.COMPLETED
    3L -> Comick.ON_HOLD
    4L -> Comick.DROPPED
    5L -> Comick.PLAN_TO_READ
    else -> throw NotImplementedError("Unknown status: $this")
}

class ComickMissingScopesException(message: String) : Exception(message)
