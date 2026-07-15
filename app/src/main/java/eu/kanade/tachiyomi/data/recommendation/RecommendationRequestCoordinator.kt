package eu.kanade.tachiyomi.data.recommendation

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Process-local coordination for recommendation requests.
 *
 * A request key may represent one source or a shared upstream host. Keeping the gate, pacing and
 * cooldown in one collaborator prevents separate manga screens from creating a serial request
 * burst or racing past a cooldown that another screen just observed.
 */
internal class RecommendationRequestCoordinator(
    private val monotonicNowNanos: () -> Long = System::nanoTime,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val randomUnit: () -> Double = SecureRandom()::nextDouble,
) {

    private val gates = ConcurrentHashMap<String, SharedGate>()
    private val pacers = ConcurrentHashMap<String, RequestPacer>()
    private val sourceBlockedUntil = ConcurrentHashMap<String, Long>()
    private val relatedBlockedUntil = ConcurrentHashMap<String, Long>()
    private val queueBlockedUntil = ConcurrentHashMap<String, Long>()
    private val relatedRateLimitFailures = ConcurrentHashMap<String, FailureSequence>()
    private val relatedTransientFailures = ConcurrentHashMap<String, FailureSequence>()
    private val queueTimeoutFailures = ConcurrentHashMap<String, FailureSequence>()

    fun semaphore(requestKey: String, maxConcurrency: Int): Semaphore {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        return gates.compute(requestKey) { _, existing ->
            when {
                existing == null -> SharedGate(maxConcurrency, Semaphore(maxConcurrency))
                existing.maxConcurrency == maxConcurrency -> existing
                else -> error("Request key $requestKey changed concurrency policy")
            }
        }!!.semaphore
    }

    suspend fun pace(requestKey: String, minimumIntervalMillis: Long) {
        if (minimumIntervalMillis <= 0L) return
        val intervalNanos = saturatingMultiply(minimumIntervalMillis, NANOS_PER_MILLISECOND)
        val pacer = pacers.computeIfAbsent(requestKey) { RequestPacer() }
        pacer.mutex.withLock {
            val current = monotonicNowNanos()
            val waitNanos = (pacer.nextAllowedNanos - current).coerceAtLeast(0L)
            if (waitNanos > 0L) {
                delayMillis(ceil(waitNanos.toDouble() / NANOS_PER_MILLISECOND).toLong())
            }
            val afterDelay = monotonicNowNanos()
            pacer.nextAllowedNanos = saturatingAdd(
                maxOf(afterDelay, pacer.nextAllowedNanos),
                intervalNanos,
            )
        }
    }

    fun sourceCooldownUntil(requestKey: String, nowMillis: Long): Long? =
        activeDeadline(sourceBlockedUntil, requestKey, nowMillis)

    fun blockSourceFor(requestKey: String, nowMillis: Long, durationMillis: Long): Long =
        blockSourceUntil(requestKey, saturatingAdd(nowMillis, durationMillis.coerceAtLeast(1L)))

    fun blockSourceUntil(requestKey: String, deadlineMillis: Long): Long =
        sourceBlockedUntil.merge(requestKey, deadlineMillis, ::maxOf) ?: deadlineMillis

    fun relatedCooldownUntil(hostKey: String, nowMillis: Long): Long? =
        activeDeadline(relatedBlockedUntil, hostKey, nowMillis)

    fun queueCooldownUntil(requestKey: String, nowMillis: Long): Long? =
        activeDeadline(queueBlockedUntil, requestKey, nowMillis)

    fun recordRelatedRateLimit(hostKey: String, nowMillis: Long, retryAfterMillis: Long?): Long {
        val strike = nextFailureStrike(
            failures = relatedRateLimitFailures,
            key = hostKey,
            nowMillis = nowMillis,
            maxStrikes = MAX_RATE_LIMIT_STRIKES,
        )
        val exponential = (RELATED_BACKOFF_BASE_MILLIS shl (strike - 1))
            .coerceAtMost(RELATED_BACKOFF_MAX_MILLIS)
        val localBackoff = withJitter(exponential, RELATED_BACKOFF_MAX_MILLIS)
        // A missing or unrealistically short Retry-After must not create a tight retry loop.
        // Longer server instructions still win and are bounded separately.
        val serverBackoff = retryAfterMillis?.coerceIn(
            RELATED_BACKOFF_MIN_MILLIS,
            SERVER_RETRY_AFTER_MAX_MILLIS,
        )
        val delay = maxOf(localBackoff, serverBackoff ?: 0L)
        return blockRelatedUntil(hostKey, saturatingAdd(nowMillis, delay))
    }

    fun recordRelatedTransientFailure(hostKey: String, nowMillis: Long): Long {
        val strike = nextFailureStrike(
            failures = relatedTransientFailures,
            key = hostKey,
            nowMillis = nowMillis,
            maxStrikes = MAX_TRANSIENT_STRIKES,
        )
        val exponential = (RELATED_TRANSIENT_BACKOFF_BASE_MILLIS shl (strike - 1))
            .coerceAtMost(RELATED_TRANSIENT_BACKOFF_MAX_MILLIS)
        val delay = withJitter(exponential, RELATED_TRANSIENT_BACKOFF_MAX_MILLIS)
        return blockRelatedUntil(hostKey, saturatingAdd(nowMillis, delay))
    }

    fun recordQueueTimeout(requestKey: String, nowMillis: Long): Long {
        val strike = nextFailureStrike(
            failures = queueTimeoutFailures,
            key = requestKey,
            nowMillis = nowMillis,
            maxStrikes = MAX_QUEUE_TIMEOUT_STRIKES,
        )
        val exponential = (QUEUE_TIMEOUT_BACKOFF_BASE_MILLIS shl (strike - 1))
            .coerceAtMost(QUEUE_TIMEOUT_BACKOFF_MAX_MILLIS)
        val delay = withJitter(exponential, QUEUE_TIMEOUT_BACKOFF_MAX_MILLIS)
        return queueBlockedUntil.merge(
            requestKey,
            saturatingAdd(nowMillis, delay),
            ::maxOf,
        ) ?: saturatingAdd(nowMillis, delay)
    }

    fun recordRelatedUnavailable(hostKey: String, nowMillis: Long): Long =
        blockRelatedUntil(hostKey, saturatingAdd(nowMillis, RELATED_UNAVAILABLE_BACKOFF_MILLIS))

    fun recordRelatedSuccess(hostKey: String) {
        relatedBlockedUntil.remove(hostKey)
        relatedRateLimitFailures.remove(hostKey)
        relatedTransientFailures.remove(hostKey)
    }

    fun recordQueueSuccess(requestKey: String) {
        queueBlockedUntil.remove(requestKey)
        queueTimeoutFailures.remove(requestKey)
    }

    private fun blockRelatedUntil(hostKey: String, deadlineMillis: Long): Long =
        relatedBlockedUntil.merge(hostKey, deadlineMillis, ::maxOf) ?: deadlineMillis

    private fun nextFailureStrike(
        failures: ConcurrentHashMap<String, FailureSequence>,
        key: String,
        nowMillis: Long,
        maxStrikes: Int,
    ): Int {
        return failures.compute(key) { _, previous ->
            val previousStrikes = if (
                previous == null || hasBeenQuietSince(previous.lastFailureAtMillis, nowMillis)
            ) {
                0
            } else {
                previous.strikes
            }
            FailureSequence(
                strikes = (previousStrikes + 1).coerceAtMost(maxStrikes),
                lastFailureAtMillis = nowMillis,
            )
        }!!.strikes
    }

    private fun hasBeenQuietSince(lastFailureAtMillis: Long, nowMillis: Long): Boolean {
        return nowMillis >= lastFailureAtMillis &&
            nowMillis - lastFailureAtMillis >= FAILURE_STRIKE_RESET_WINDOW_MILLIS
    }

    private fun withJitter(baseMillis: Long, maximumMillis: Long): Long {
        val jitter = (baseMillis * BACKOFF_JITTER_RATIO * randomUnit().coerceIn(0.0, 1.0)).toLong()
        return saturatingAdd(baseMillis, jitter).coerceAtMost(maximumMillis)
    }

    private fun activeDeadline(
        deadlines: ConcurrentHashMap<String, Long>,
        key: String,
        nowMillis: Long,
    ): Long? {
        val deadline = deadlines[key] ?: return null
        if (nowMillis < deadline) return deadline
        deadlines.remove(key, deadline)
        return null
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE
        return left + right
    }

    private fun saturatingMultiply(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE
        return left * right
    }

    private data class SharedGate(
        val maxConcurrency: Int,
        val semaphore: Semaphore,
    )

    private class RequestPacer {
        val mutex = Mutex()
        var nextAllowedNanos: Long = 0L
    }

    private data class FailureSequence(
        val strikes: Int,
        val lastFailureAtMillis: Long,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val RELATED_BACKOFF_MIN_MILLIS = 5_000L
        const val RELATED_BACKOFF_BASE_MILLIS = 60 * 1000L
        const val RELATED_BACKOFF_MAX_MILLIS = 15 * 60 * 1000L
        const val SERVER_RETRY_AFTER_MAX_MILLIS = 24 * 60 * 60 * 1000L
        const val RELATED_TRANSIENT_BACKOFF_BASE_MILLIS = 30_000L
        const val RELATED_TRANSIENT_BACKOFF_MAX_MILLIS = 15 * 60 * 1000L
        const val QUEUE_TIMEOUT_BACKOFF_BASE_MILLIS = 30_000L
        const val QUEUE_TIMEOUT_BACKOFF_MAX_MILLIS = 15 * 60 * 1000L
        const val RELATED_UNAVAILABLE_BACKOFF_MILLIS = 6 * 60 * 60 * 1000L
        const val FAILURE_STRIKE_RESET_WINDOW_MILLIS = 30 * 60 * 1000L
        const val BACKOFF_JITTER_RATIO = 0.20
        const val MAX_RATE_LIMIT_STRIKES = 6
        const val MAX_TRANSIENT_STRIKES = 6
        const val MAX_QUEUE_TIMEOUT_STRIKES = 6
    }
}
