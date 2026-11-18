package mihon.core.common.extensions

import kotlin.time.Instant

/**
 * Reads epoch milliseconds from a format that writes 0 for a time it doesn't know, such as the source API, a tracker
 * or a backup.
 */
fun Long.toInstantOrNull(): Instant? = takeIf { it != 0L }?.let(Instant::fromEpochMilliseconds)

/**
 * Writes epoch milliseconds to a format that takes 0 for a time it doesn't know.
 */
fun Instant?.toEpochMillisOrZero(): Long = this?.toEpochMilliseconds() ?: 0L
