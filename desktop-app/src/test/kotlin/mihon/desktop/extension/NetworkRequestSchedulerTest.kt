package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.extension.ipc.RequestPriority
import org.junit.jupiter.api.Test

class NetworkRequestSchedulerTest {
    @Test
    fun `reader is admitted before queued bulk pages and cancellation releases waiting slot`(): Unit = runBlocking {
        NetworkRequestScheduler(totalLimit = 1, hostLimit = 1).use { scheduler ->
            val release = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "a", 0) { release.await() }
            }
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "cancelled", 2) { error("Cancelled request ran") }
            }
            val bulk = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "a", RequestPriority.BACKGROUND) { order.add("bulk") }
            }
            val reader = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "b", RequestPriority.READER) { order.add("reader") }
            }
            cancelled.cancelAndJoin()
            release.complete(Unit)
            withTimeout(1_000) {
                first.await()
                reader.await()
                bulk.await()
            }
            order shouldBe listOf("reader", "bulk")
        }
    }

    @Test
    fun `same priority rotates source owners and leaves a host slot for reading`(): Unit = runBlocking {
        NetworkRequestScheduler(totalLimit = 3, hostLimit = 2).use { scheduler ->
            val release = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "a", 0) { release.await() }
            }
            val same = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "a", 0) { order.add("a") }
            }
            val other = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("host", "b", 0) { order.add("b") }
            }
            scheduler.withPermit("host", "reader", 2) { order.add("reader") }
            release.complete(Unit)
            withTimeout(1_000) {
                first.await()
                same.await()
                other.await()
            }
            order.first() shouldBe "reader"
            order.size shouldBe 3
        }
    }

    @Test
    fun `retry after gates a host without delaying another host`(): Unit = runBlocking {
        NetworkRequestScheduler().use { scheduler ->
            scheduler.deferHost("limited", 150)
            var ranLimited = false
            val limited = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("limited", "a", 2) { ranLimited = true }
            }
            scheduler.withPermit("healthy", "b", 0) { ranLimited shouldBe false }
            withTimeout(1_000) { limited.await() }
            ranLimited shouldBe true
        }
    }
}
