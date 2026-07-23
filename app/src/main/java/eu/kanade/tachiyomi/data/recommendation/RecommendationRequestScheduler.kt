package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** Serializes recommendation-only requests without touching a source's shared HTTP client. */
internal class RecommendationRequestScheduler(
    private val minimumIntervalMillis: Long = DEFAULT_MINIMUM_INTERVAL_MILLIS,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val monotonicNowNanos: () -> Long = System::nanoTime,
    private val wallNowMillis: () -> Long = System::currentTimeMillis,
) {
    private val states = ConcurrentHashMap<Long, SourceState>()

    init {
        require(minimumIntervalMillis >= DEFAULT_MINIMUM_INTERVAL_MILLIS)
    }

    suspend fun <T> execute(
        sourceId: Long,
        block: suspend () -> T,
    ): RecommendationRequestResult<T> {
        val state = states.computeIfAbsent(sourceId) { SourceState() }
        cooldownUntil(sourceId)?.let { return RecommendationRequestResult.RateLimited(it) }
        return state.gate.withPermit {
            cooldownUntil(sourceId)?.let { return@withPermit RecommendationRequestResult.RateLimited(it) }
            val nowNanos = monotonicNowNanos()
            val waitNanos = state.nextStartNanos?.let { (it - nowNanos).coerceAtLeast(0L) } ?: 0L
            if (waitNanos > 0L) delayMillis(nanosToCeilMillis(waitNanos))
            val startedAt = maxOf(monotonicNowNanos(), state.nextStartNanos ?: Long.MIN_VALUE)
            state.nextStartNanos = saturatingAdd(startedAt, millisToNanos(minimumIntervalMillis))
            try {
                RecommendationRequestResult.Success(block()).also { recordSuccess(sourceId) }
            } catch (error: HttpException) {
                if (error.code != HTTP_TOO_MANY_REQUESTS) throw error
                RecommendationRequestResult.RateLimited(record429(sourceId, error.retryAfter))
            }
        }
    }

    /** Records a 429 once. Callers decide when a new page or explicit refresh may try again. */
    fun record429(
        sourceId: Long,
        retryAfter: String? = null,
        nowMillis: Long = wallNowMillis(),
    ): Long {
        val state = states.computeIfAbsent(sourceId) { SourceState() }
        return synchronized(state) {
            state.consecutiveRateLimits = (state.consecutiveRateLimits + 1).coerceAtMost(BACKOFF_MILLIS.size)
            val retryAfterMillis = parseRetryAfterMillis(retryAfter, nowMillis)
            val delay = retryAfterMillis ?: BACKOFF_MILLIS[state.consecutiveRateLimits - 1]
            val retryAt = saturatingAdd(nowMillis, delay.coerceAtLeast(0L))
            state.cooldownUntilMillis = retryAt
            retryAt
        }
    }

    fun recordSuccess(sourceId: Long) {
        val state = states[sourceId] ?: return
        synchronized(state) {
            state.cooldownUntilMillis = 0L
            state.consecutiveRateLimits = 0
        }
    }

    fun cooldownUntil(sourceId: Long, nowMillis: Long = wallNowMillis()): Long? {
        val retryAt = states[sourceId]?.cooldownUntilMillis ?: return null
        return retryAt.takeIf { it > nowMillis }
    }

    fun clear(sourceId: Long) {
        states.remove(sourceId)
    }

    internal companion object {
        const val DEFAULT_MINIMUM_INTERVAL_MILLIS = 1_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
        private val BACKOFF_MILLIS = longArrayOf(15_000L, 30_000L, 60_000L, 120_000L, 300_000L)

        fun parseRetryAfterMillis(header: String?, nowMillis: Long): Long? {
            val value = header?.trim()?.takeIf(String::isNotEmpty) ?: return null
            value.toLongOrNull()?.takeIf { it >= 0L }?.let { seconds ->
                return if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
            }
            return runCatching {
                val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
                (retryAt - nowMillis).coerceAtLeast(0L)
            }.getOrNull()
        }
    }

    private class SourceState {
        val gate = Semaphore(1)
        var nextStartNanos: Long? = null

        @Volatile
        var cooldownUntilMillis: Long = 0L

        var consecutiveRateLimits: Int = 0
    }
}

internal sealed interface RecommendationRequestResult<out T> {
    data class Success<T>(val value: T) : RecommendationRequestResult<T>
    data class RateLimited(val retryAtMillis: Long) : RecommendationRequestResult<Nothing>
}

private fun nanosToCeilMillis(nanos: Long): Long {
    return nanos / 1_000_000L + if (nanos % 1_000_000L == 0L) 0L else 1L
}

private fun millisToNanos(millis: Long): Long {
    return if (millis > Long.MAX_VALUE / 1_000_000L) Long.MAX_VALUE else millis * 1_000_000L
}

private fun saturatingAdd(left: Long, right: Long): Long {
    return if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
