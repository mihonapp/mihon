package eu.kanade.tachiyomi.data.translation.overlay

import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Spaces out requests so the free tier limits are respected. Google does not publish fixed numbers (they depend on
 * model and account), so on top of a user configurable base delay the pacer slows down by itself after a rate limit
 * response and speeds up again after successes.
 */
class RequestPacer(private val clock: () -> Long = System::currentTimeMillis) {

    private var extraMillis = 0L
    private var nextAllowedAt = 0L

    /** Suspends until a request may be sent. Safe to call from several coroutines. */
    suspend fun awaitTurn(baseIntervalMillis: Long) {
        val wait = reserveSlot(baseIntervalMillis)
        if (wait > 0) delay(wait)
    }

    /** Reserves the next slot and returns how long the caller has to wait for it. */
    @Synchronized
    internal fun reserveSlot(baseIntervalMillis: Long): Long {
        val now = clock()
        val start = max(now, nextAllowedAt)
        nextAllowedAt = start + baseIntervalMillis + extraMillis
        return start - now
    }

    /** Call after a rate limit response. Waits at least [retryAfterMillis] when Google gave a hint. */
    @Synchronized
    fun onRateLimited(retryAfterMillis: Long?) {
        extraMillis = if (extraMillis == 0L) INITIAL_EXTRA_MILLIS else min(MAX_EXTRA_MILLIS, extraMillis * 2)
        val pause = retryAfterMillis ?: (extraMillis + MIN_PAUSE_MILLIS)
        nextAllowedAt = max(nextAllowedAt, clock() + pause)
    }

    /** Call after a successful request. */
    @Synchronized
    fun onSuccess() {
        extraMillis = (extraMillis * SPEED_UP_FACTOR).toLong()
        if (extraMillis < INITIAL_EXTRA_MILLIS / 4) extraMillis = 0L
    }

    @Synchronized
    internal fun currentExtraMillis() = extraMillis

    private companion object {
        const val INITIAL_EXTRA_MILLIS = 4_000L
        const val MAX_EXTRA_MILLIS = 60_000L
        const val MIN_PAUSE_MILLIS = 2_000L
        const val SPEED_UP_FACTOR = 0.7
    }
}
