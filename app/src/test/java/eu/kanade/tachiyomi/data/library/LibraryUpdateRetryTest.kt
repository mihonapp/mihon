package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryUpdateRetryTest {

    @Test
    fun `successful updates do not wait or retry`() = runTest {
        val semaphore = Semaphore(5)
        val retry = LibraryUpdateRetry(semaphore, testScheduler.timeSource)
        var attempts = 0

        val result = retry.run {
            attempts++
            "updated"
        }

        assertEquals("updated", result)
        assertEquals(1, attempts)
        assertEquals(0L, currentTime)
        assertEquals(5, semaphore.availablePermits)
    }

    @Test
    fun `429 retries use exponential backoff and can recover on the last attempt`() = runTest {
        val retry = LibraryUpdateRetry(Semaphore(5), testScheduler.timeSource)
        val attempts = mutableListOf<Long>()

        val result = retry.run {
            attempts.add(currentTime)
            if (attempts.size <= 3) throw HttpException(429)
            "updated"
        }

        assertEquals("updated", result)
        assertEquals(listOf(0L, 10_000L, 30_000L, 70_000L), attempts)
    }

    @Test
    fun `metadata result failures are retried before completing the title`() = runTest {
        val retry = LibraryUpdateRetry(Semaphore(5), testScheduler.timeSource)
        val attempts = mutableListOf<Long>()
        var completed = 0
        var failures = 0

        try {
            retry.run {
                attempts.add(currentTime)
                val result = if (attempts.size <= 3) {
                    Result.failure<Unit>(HttpException(429))
                } else {
                    Result.success(Unit)
                }
                result.getOrThrow()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            failures++
        }
        completed++

        assertEquals(listOf(0L, 10_000L, 30_000L, 70_000L), attempts)
        assertEquals(1, completed)
        assertEquals(0, failures)
    }

    @Test
    fun `cancellation returned by metadata update stops the source queue without reporting failure`() = runTest {
        val semaphore = Semaphore(5)
        val retry = LibraryUpdateRetry(semaphore, testScheduler.timeSource)
        val cancellation = CancellationException("Metadata update cancelled")
        val attempts = mutableListOf<String>()
        var failures = 0
        var completed = 0

        val failure = runCatching {
            listOf("first", "second").forEach { title ->
                try {
                    retry.run {
                        attempts.add(title)
                        Result.failure<Unit>(cancellation).getOrThrow()
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    failures++
                }
                completed++
            }
        }.exceptionOrNull()

        assertSame(cancellation, failure)
        assertEquals(listOf("first"), attempts)
        assertEquals(0, failures)
        assertEquals(0, completed)
        assertEquals(0L, currentTime)
        assertEquals(5, semaphore.availablePermits)
    }

    @Test
    fun `exhausted titles fail once and remaining titles honor the source cooldown and delay cap`() = runTest {
        val retry = LibraryUpdateRetry(Semaphore(5), testScheduler.timeSource)
        val error = HttpException(429)
        val attempts = mutableListOf<Pair<String, Long>>()
        val failures = mutableListOf<String>()
        val completed = mutableListOf<String>()

        listOf("first", "second", "third").forEach { title ->
            try {
                retry.run {
                    attempts.add(title to currentTime)
                    if (title != "third") throw error
                    completed.add(title)
                }
            } catch (e: HttpException) {
                assertSame(error, e)
                failures.add(title)
            }
        }

        assertEquals(listOf("first", "second"), failures)
        assertEquals(listOf("third"), completed)
        assertEquals(
            listOf(
                "first" to 0L,
                "first" to 10_000L,
                "first" to 30_000L,
                "first" to 70_000L,
                "second" to 150_000L,
                "second" to 310_000L,
                "second" to 610_000L,
                "second" to 910_000L,
                "third" to 1_210_000L,
            ),
            attempts,
        )
    }

    @Test
    fun `success resets the backoff for the next title`() = runTest {
        val retry = LibraryUpdateRetry(Semaphore(5), testScheduler.timeSource)
        val attempts = mutableListOf<Long>()
        var firstAttempts = 0
        retry.run {
            attempts.add(currentTime)
            if (++firstAttempts <= 2) throw HttpException(429)
        }
        var secondAttempts = 0
        retry.run {
            attempts.add(currentTime)
            if (++secondAttempts == 1) throw HttpException(429)
        }

        assertEquals(listOf(0L, 10_000L, 30_000L, 30_000L, 40_000L), attempts)
    }

    @Test
    fun `time spent between titles counts toward the retained cooldown`() = runTest {
        listOf(30_000L, 100_000L).forEach { elapsed ->
            val retry = LibraryUpdateRetry(Semaphore(5), testScheduler.timeSource)
            val error = HttpException(429)
            val startedAt = currentTime
            val failure = runCatching {
                retry.run<Unit> { throw error }
            }.exceptionOrNull()

            assertSame(error, failure)
            assertEquals(startedAt + 70_000L, currentTime)
            delay(elapsed)
            retry.run {
                assertEquals(startedAt + 70_000L + maxOf(80_000L, elapsed), currentTime)
            }
        }
    }

    @Test
    fun `non-429 errors are not retried or detected by message text`() = runTest {
        val retry = LibraryUpdateRetry(Semaphore(5), testScheduler.timeSource)
        val errors =
            listOf(HttpException(403), HttpException(500), IOException(), IllegalStateException("HTTP error 429"))

        errors.forEach { error ->
            var attempts = 0
            val failure = runCatching {
                retry.run {
                    attempts++
                    throw error
                }
            }.exceptionOrNull()

            assertSame(error, failure)
            assertEquals(1, attempts)
        }
        assertEquals(0L, currentTime)
    }

    @Test
    fun `five backing off sources do not block a sixth source`() = runTest {
        val semaphore = Semaphore(5)
        val rateLimitedAttempts = IntArray(5)
        val sources = List(5) { source ->
            async {
                val retry = LibraryUpdateRetry(semaphore, testScheduler.timeSource)
                retry.run {
                    if (++rateLimitedAttempts[source] == 1) throw HttpException(429)
                }
            }
        }
        var otherSourceCompleted = false
        val otherSource = launch {
            LibraryUpdateRetry(semaphore, testScheduler.timeSource).run {
                otherSourceCompleted = true
            }
        }

        runCurrent()

        assertTrue(otherSourceCompleted)
        assertEquals(List(5) { 1 }, rateLimitedAttempts.toList())
        assertEquals(5, semaphore.availablePermits)
        assertEquals(0L, currentTime)
        sources.awaitAll()
        otherSource.join()
        assertEquals(10_000L, currentTime)
        assertEquals(List(5) { 2 }, rateLimitedAttempts.toList())
    }

    @Test
    fun `active attempts still respect the shared concurrency limit`() = runTest {
        val semaphore = Semaphore(5)
        var active = 0
        var maximumActive = 0

        List(6) {
            async {
                LibraryUpdateRetry(semaphore, testScheduler.timeSource).run {
                    active++
                    maximumActive = maxOf(maximumActive, active)
                    try {
                        delay(1_000)
                    } finally {
                        active--
                    }
                }
            }
        }.awaitAll()

        assertEquals(5, maximumActive)
        assertEquals(0, active)
        assertEquals(2_000L, currentTime)
        assertEquals(5, semaphore.availablePermits)
    }

    @Test
    fun `cancellation during backoff stops retries and the remaining source queue`() = runTest {
        val semaphore = Semaphore(5)
        val retry = LibraryUpdateRetry(semaphore, testScheduler.timeSource)
        var attempts = 0
        var nextTitleStarted = false
        val job = launch {
            retry.run<Unit> {
                attempts++
                throw HttpException(429)
            }
            nextTitleStarted = true
        }

        runCurrent()
        job.cancelAndJoin()

        assertEquals(1, attempts)
        assertTrue(job.isCancelled)
        assertEquals(false, nextTitleStarted)
        assertEquals(0L, currentTime)
        assertEquals(5, semaphore.availablePermits)
    }

    @Test
    fun `cancellation while waiting for a permit does not start an update or leak a permit`() = runTest {
        val semaphore = Semaphore(5, acquiredPermits = 5)
        val retry = LibraryUpdateRetry(semaphore, testScheduler.timeSource)
        var attempts = 0
        val job = launch {
            retry.run { attempts++ }
        }

        runCurrent()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(0, attempts)
        assertEquals(0, semaphore.availablePermits)
        repeat(5) { semaphore.release() }
        retry.run { attempts++ }
        assertEquals(1, attempts)
        assertEquals(5, semaphore.availablePermits)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `cancellation from an update is rethrown without retry`() = runTest {
        val semaphore = Semaphore(5)
        val retry = LibraryUpdateRetry(semaphore, testScheduler.timeSource)
        val cancellation = CancellationException("cancelled")
        var attempts = 0

        val failure = runCatching {
            retry.run {
                attempts++
                throw cancellation
            }
        }.exceptionOrNull()

        assertSame(cancellation, failure)
        assertEquals(1, attempts)
        assertEquals(0L, currentTime)
        assertEquals(5, semaphore.availablePermits)
    }
}
