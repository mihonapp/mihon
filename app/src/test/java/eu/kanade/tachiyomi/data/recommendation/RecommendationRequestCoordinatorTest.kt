package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecommendationRequestCoordinatorTest {

    @Test
    fun `shared host requests are serialized and spaced`() = runTest {
        val coordinator = RecommendationRequestCoordinator(
            monotonicNowNanos = { testScheduler.currentTime * 1_000_000L },
            randomUnit = { 0.0 },
        )
        val semaphore = coordinator.semaphore("host:nhentai.net", maxConcurrency = 1)
        val starts = mutableListOf<Long>()

        (1..3).map {
            async {
                semaphore.withPermit {
                    coordinator.pace("host:nhentai.net", minimumIntervalMillis = 1_000L)
                    starts += testScheduler.currentTime
                }
            }
        }.awaitAll()

        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }

    @Test
    fun `related rate limit honors server delay and uses a safe fallback`() {
        val coordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 })
        val first = coordinator.recordRelatedRateLimit("nhentai.net", 1_000L, retryAfterMillis = null)
        assertEquals(1_000L + 60 * 1_000L, first)
        assertEquals(first, coordinator.relatedCooldownUntil("nhentai.net", first - 1L))
        assertNull(coordinator.relatedCooldownUntil("nhentai.net", first))

        val second = coordinator.recordRelatedRateLimit("nhentai.net", first, retryAfterMillis = null)
        assertEquals(first + 2 * 60 * 1_000L, second)
        assertNull(coordinator.relatedCooldownUntil("nhentai.net", second))

        val serverDelay = coordinator.recordRelatedRateLimit(
            "nhentai.net",
            second,
            retryAfterMillis = 60 * 60 * 1_000L,
        )
        assertEquals(second + 60 * 60 * 1_000L, serverDelay)
        assertNull(coordinator.relatedCooldownUntil("nhentai.net", serverDelay))

        val capped = coordinator.recordRelatedRateLimit(
            "nhentai.net",
            serverDelay,
            retryAfterMillis = Long.MAX_VALUE,
        )
        assertEquals(serverDelay + 24 * 60 * 60 * 1_000L, capped)

        coordinator.recordRelatedSuccess("nhentai.net")
        val shortServerDelay = coordinator.recordRelatedRateLimit(
            "nhentai.net",
            capped,
            retryAfterMillis = 1_000L,
        )
        assertEquals(capped + 60 * 1_000L, shortServerDelay)
    }

    @Test
    fun `source cooldown never shortens and saturates safely`() {
        val coordinator = RecommendationRequestCoordinator()
        assertEquals(5_000L, coordinator.blockSourceUntil("host", 5_000L))
        assertEquals(5_000L, coordinator.blockSourceUntil("host", 4_000L))
        assertEquals(Long.MAX_VALUE, coordinator.blockSourceFor("other", Long.MAX_VALUE - 1, 10L))
    }

    @Test
    fun `transient failures back off exponentially and success resets the sequence`() {
        val coordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 })

        val first = coordinator.recordRelatedTransientFailure("nhentai.net", 1_000L)
        assertEquals(31_000L, first)
        assertNull(coordinator.relatedCooldownUntil("nhentai.net", first))

        val second = coordinator.recordRelatedTransientFailure("nhentai.net", first)
        assertEquals(91_000L, second)
        assertNull(coordinator.relatedCooldownUntil("nhentai.net", second))

        coordinator.recordRelatedSuccess("nhentai.net")
        val reset = coordinator.recordRelatedTransientFailure("nhentai.net", second)
        assertEquals(121_000L, reset)
    }

    @Test
    fun `failure sequence resets after a quiet window`() {
        val coordinator = RecommendationRequestCoordinator(randomUnit = { 0.0 })
        val first = coordinator.recordRelatedRateLimit("nhentai.net", 1_000L, retryAfterMillis = null)
        val second = coordinator.recordRelatedRateLimit("nhentai.net", first, retryAfterMillis = null)
        val afterQuietWindow = second + 30 * 60 * 1_000L

        val reset = coordinator.recordRelatedRateLimit(
            "nhentai.net",
            afterQuietWindow,
            retryAfterMillis = null,
        )

        assertEquals(afterQuietWindow + 60 * 1_000L, reset)
    }

    @Test
    fun `queue timeouts use shared exponential backoff with jitter and reset on success`() {
        val coordinator = RecommendationRequestCoordinator(randomUnit = { 0.5 })

        val first = coordinator.recordQueueTimeout("host:nhentai.net", 1_000L)
        assertEquals(34_000L, first)
        assertEquals(first, coordinator.queueCooldownUntil("host:nhentai.net", first - 1L))
        assertNull(coordinator.queueCooldownUntil("host:nhentai.net", first))

        val second = coordinator.recordQueueTimeout("host:nhentai.net", first)
        assertEquals(first + 66_000L, second)

        coordinator.recordQueueSuccess("host:nhentai.net")
        val reset = coordinator.recordQueueTimeout("host:nhentai.net", second)
        assertEquals(second + 33_000L, reset)
    }

    @Test
    fun `related cache is target scoped defensive and supports stale fallback`() {
        val cache = RelatedRecommendationCache(
            maxEntries = 2,
            freshTtlMillis = 100L,
            staleTtlMillis = 1_000L,
            negativeTtlMillis = 50L,
        )
        val item = manga("one")
        cache.put("source-a:1", listOf(item), nowMillis = 0L)

        val fresh = cache.getFresh("source-a:1", 99L)!!
        assertNotSame(item, fresh.single())
        fresh.single().title = "mutated"
        assertEquals("one", cache.getFresh("source-a:1", 99L)!!.single().title)
        assertNull(cache.getFresh("source-b:1", 99L))
        assertNull(cache.getFresh("source-a:1", 100L))
        assertEquals("one", cache.getStale("source-a:1", 999L)!!.single().title)
        assertNull(cache.getStale("source-a:1", 1_000L))

        cache.put("source-a:empty", emptyList(), nowMillis = 1_000L)
        assertEquals(emptyList<SManga>(), cache.getFresh("source-a:empty", 1_049L))
        assertEquals(1_050L, cache.negativeCacheUntil("source-a:empty", 1_049L))
        assertNull(cache.getFresh("source-a:empty", 1_050L))
        assertNull(cache.negativeCacheUntil("source-a:empty", 1_050L))
    }

    private fun manga(value: String): SManga = SManga.create().apply {
        url = value
        title = value
    }
}
