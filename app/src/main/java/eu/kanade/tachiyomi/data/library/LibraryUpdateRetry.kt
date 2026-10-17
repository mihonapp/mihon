package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class LibraryUpdateRetry(
    private val semaphore: Semaphore,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var nextDelay = INITIAL_DELAY
    private var retryAt: TimeMark? = null

    suspend fun <T> run(block: suspend () -> T): T {
        var retries = 0
        while (true) {
            retryAt?.let { delay(-it.elapsedNow()) }
            currentCoroutineContext().ensureActive()

            try {
                val result = semaphore.withPermit { block() }
                nextDelay = INITIAL_DELAY
                retryAt = null
                return result
            } catch (e: HttpException) {
                if (e.code != 429) throw e

                // Keep the source cooldown even when this title has exhausted its retries.
                retryAt = timeSource.markNow() + nextDelay
                nextDelay = (nextDelay * 2).coerceAtMost(MAX_DELAY)
                if (retries++ >= MAX_RETRIES) throw e
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        val INITIAL_DELAY = 10.seconds
        val MAX_DELAY = 5.minutes
    }
}
