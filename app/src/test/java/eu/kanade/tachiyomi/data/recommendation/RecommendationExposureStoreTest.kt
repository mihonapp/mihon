package eu.kanade.tachiyomi.data.recommendation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RecommendationExposureStoreTest {

    @Test
    fun `exposures are isolated by target manga within one source`() {
        val store = RecommendationExposureStore()

        store.recordShown(1L, listOf("from-a"), targetKey = "manga-a")
        store.recordShown(1L, listOf("from-b"), targetKey = "manga-b")

        assertEquals(setOf("from-a"), store.snapshot(1L, "manga-a").recentKeys)
        assertEquals(setOf("from-b"), store.snapshot(1L, "manga-b").recentKeys)
    }

    @Test
    fun `exposures are isolated by source id`() {
        val store = RecommendationExposureStore(now = { 1_000L })

        store.recordShown(1L, listOf("shared", "source-one"))
        store.recordShown(2L, listOf("source-two"))

        assertEquals(setOf("shared", "source-one"), store.snapshot(1L).recentKeys)
        assertEquals(setOf("source-two"), store.snapshot(2L).recentKeys)
        assertTrue(store.snapshot(3L).recentKeys.isEmpty())
    }

    @Test
    fun `only the forty most recently shown fingerprints are retained`() {
        var now = 1_000L
        val store = RecommendationExposureStore(now = { now })

        store.recordShown(1L, (0..40).map { "work-$it" })
        now += 1
        store.recordShown(1L, listOf("work-10"))

        val snapshot = store.snapshot(1L)
        assertEquals(40, snapshot.recentKeys.size)
        assertFalse("work-0" in snapshot.recentKeys)
        assertEquals(setOf("work-1"), snapshot.leastRecentlyShownWorksFirst.first())
        assertEquals(setOf("work-10"), snapshot.leastRecentlyShownWorksFirst.last())
    }

    @Test
    fun `fingerprints expire after thirty minutes using the injected clock`() {
        var now = 5_000L
        val store = RecommendationExposureStore(now = { now })

        store.recordShown(1L, listOf("work"))
        now += 30 * 60 * 1000L - 1
        assertEquals(setOf("work"), store.snapshot(1L).recentKeys)

        now += 1
        assertTrue(store.snapshot(1L).recentKeys.isEmpty())
    }

    @Test
    fun `creator alias sets merge into one recently shown work`() {
        var now = 1_000L
        val store = RecommendationExposureStore(now = { now })
        store.recordShownWorks(1L, listOf(setOf("title:alice", "title:bob")))

        now += 1
        store.recordShownWorks(1L, listOf(setOf("title:bob")))

        val snapshot = store.snapshot(1L)
        assertEquals(1, snapshot.leastRecentlyShownWorksFirst.size)
        assertEquals(setOf("title:alice", "title:bob"), snapshot.recentKeys)
        assertEquals(0, snapshot.exposureIndex(setOf("title:alice")))
    }

    @Test
    fun `snapshot is stable and retains oldest first exposure indexes`() {
        var now = 1_000L
        val store = RecommendationExposureStore(now = { now })
        store.recordShown(1L, listOf("recent-a", "recent-b"))
        val snapshot = store.snapshot(1L)

        now += 1
        store.recordShown(1L, listOf("later"))

        assertEquals(listOf(1_000L, 1_000L), snapshot.exposures.map(RecommendationExposure::shownAtMillis))
        assertEquals(0, snapshot.exposureIndex(setOf("recent-a")))
        assertEquals(1, snapshot.exposureIndex(setOf("recent-b")))
        assertNull(snapshot.exposureIndex(setOf("unseen")))
        assertFalse("later" in snapshot.recentKeys)
    }

    @Test
    fun `concurrent reads and writes remain bounded`() {
        val store = RecommendationExposureStore(now = { 1_000L })
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)

        repeat(8) { worker ->
            thread(name = "exposure-store-test-$worker") {
                start.await()
                repeat(100) { index ->
                    store.recordShown(1L, listOf("$worker-$index"))
                    store.snapshot(1L)
                }
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(40, store.snapshot(1L).recentKeys.size)
    }
}
