package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationRequestSchedulerTest {

    @Test
    fun `requests are serial and starts are spaced by one second`() = runTest {
        var active = 0
        var maxActive = 0
        val starts = mutableListOf<Long>()
        val scheduler = RecommendationRequestScheduler(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            wallNowMillis = { testScheduler.currentTime },
        )

        repeat(3) {
            launch {
                scheduler.execute(1L) {
                    starts += testScheduler.currentTime
                    active++
                    maxActive = maxOf(maxActive, active)
                    delay(250L)
                    active--
                }
            }
        }
        advanceUntilIdle()

        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
        assertEquals(1, maxActive)
    }

    @Test
    fun `waiting request is promptly cancellable`() = runTest {
        var invocations = 0
        val scheduler = RecommendationRequestScheduler(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            wallNowMillis = { testScheduler.currentTime },
        )
        val first = launch {
            scheduler.execute(1L) {
                invocations++
                delay(2_000L)
            }
        }
        val waiting = launch {
            scheduler.execute(1L) { invocations++ }
        }

        runCurrent()
        waiting.cancel()
        advanceUntilIdle()

        assertTrue(waiting.isCancelled)
        assertTrue(first.isCompleted)
        assertEquals(1, invocations)
    }

    @Test
    fun `429 uses Retry-After and never retries the block`() = runTest {
        var now = 1_000L
        var calls = 0
        val scheduler = RecommendationRequestScheduler(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            wallNowMillis = { now },
        )

        val limited = scheduler.execute(1L) {
            calls++
            throw HttpException(429, "3")
        }
        val blocked = scheduler.execute(1L) { calls++ }

        assertEquals(1, calls)
        assertEquals(
            4_000L,
            assertInstanceOf(RecommendationRequestResult.RateLimited::class.java, limited).retryAtMillis,
        )
        assertEquals(
            4_000L,
            assertInstanceOf(RecommendationRequestResult.RateLimited::class.java, blocked).retryAtMillis,
        )
        now = 4_000L
        assertNull(scheduler.cooldownUntil(1L))
    }

    @Test
    fun `fallback backoff is truncated and success clears failure count`() {
        val scheduler = RecommendationRequestScheduler()
        val expected = listOf(15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 300_000L)

        expected.forEachIndexed { index, duration ->
            assertEquals(index + duration, scheduler.record429(1L, nowMillis = index.toLong()))
        }
        scheduler.recordSuccess(1L)

        assertNull(scheduler.cooldownUntil(1L, 0L))
        assertEquals(15_000L, scheduler.record429(1L, nowMillis = 0L))
    }

    @Test
    fun `Retry-After supports seconds and HTTP dates`() {
        val now = 1_000L

        assertEquals(5_000L, RecommendationRequestScheduler.parseRetryAfterMillis("5", now))
        assertEquals(
            1_445_498_879_000L,
            RecommendationRequestScheduler.parseRetryAfterMillis("Thu, 22 Oct 2015 07:28:00 GMT", now),
        )
        assertNull(RecommendationRequestScheduler.parseRetryAfterMillis("invalid", now))
    }

    @Test
    fun `cooldown is isolated by source ID`() {
        val scheduler = RecommendationRequestScheduler()
        scheduler.record429(1L, "30", nowMillis = 1_000L)

        assertEquals(31_000L, scheduler.cooldownUntil(1L, 1_001L))
        assertNull(scheduler.cooldownUntil(2L, 1_001L))
    }
}
