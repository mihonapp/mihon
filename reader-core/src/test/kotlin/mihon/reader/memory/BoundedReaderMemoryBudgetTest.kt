package mihon.reader.memory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoundedReaderMemoryBudgetTest {
    @Test
    fun `try reserve accounts both kinds and close is idempotent`() {
        val budget = BoundedReaderMemoryBudget(10)
        val decoded = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 4))
        val cached = requireNotNull(budget.tryReserve(MemoryKind.CACHE_RESIDENT, 6))
        budget.metrics shouldBe ReaderMemoryMetrics(10, 4, 6)
        budget.tryReserve(MemoryKind.DECODED_OUTPUT, 1) shouldBe null
        decoded.close()
        decoded.close()
        cached.close()
        budget.metrics shouldBe ReaderMemoryMetrics(10, 0, 0)
    }

    @Test
    fun `suspending waiters are granted FIFO and cancellation removes a waiter`() = runTest {
        val budget = BoundedReaderMemoryBudget(10)
        val blocker = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 10))
        val order = mutableListOf<String>()
        val first = async { budget.reserve(MemoryKind.DECODED_OUTPUT, 8).also { order += "first" } }
        runCurrent()
        val cancelled = async { budget.reserve(MemoryKind.DECODED_OUTPUT, 1) }
        runCurrent()
        val second = async { budget.reserve(MemoryKind.DECODED_OUTPUT, 2).also { order += "second" } }
        runCurrent()
        cancelled.cancelAndJoin()
        blocker.close()
        runCurrent()
        order.shouldContainExactly("first", "second")
        first.await().close()
        second.await().close()
        budget.metrics shouldBe ReaderMemoryMetrics(10, 0, 0)
    }

    @Test
    fun `immediate suspending reservation does not leave a child job behind`() = runTest {
        val budget = BoundedReaderMemoryBudget(10)
        budget.reserve(MemoryKind.DECODED_OUTPUT, 2).close()
        budget.metrics shouldBe ReaderMemoryMetrics(10, 0, 0)
    }

    @Test
    fun `transfer changes ownership exactly once`() {
        val budget = BoundedReaderMemoryBudget(10)
        val original = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 7))
        val transferred = original.transferTo(MemoryKind.CACHE_RESIDENT)
        budget.metrics shouldBe ReaderMemoryMetrics(10, 0, 7)
        shouldThrow<IllegalStateException> { original.transferTo(MemoryKind.DECODED_OUTPUT) }
        original.close()
        transferred.close()
        transferred.close()
        budget.metrics shouldBe ReaderMemoryMetrics(10, 0, 0)
    }

    @Test
    fun `pressure callback may query budget because it runs outside accounting lock`() {
        lateinit var budget: BoundedReaderMemoryBudget
        var callbackMetrics: ReaderMemoryMetrics? = null
        budget = BoundedReaderMemoryBudget(1) { callbackMetrics = budget.metrics }
        val lease = requireNotNull(budget.tryReserve(MemoryKind.DECODED_OUTPUT, 1))
        budget.tryReserve(MemoryKind.CACHE_RESIDENT, 1) shouldBe null
        callbackMetrics shouldBe ReaderMemoryMetrics(1, 1, 0)
        lease.close()
    }

    @Test
    fun `oversized and closed reservations fail deterministically`() {
        val budget = BoundedReaderMemoryBudget(4)
        shouldThrow<ReaderFailure.LimitExceeded> { budget.tryReserve(MemoryKind.DECODED_OUTPUT, 5) }
        budget.close()
        budget.close()
        shouldThrow<ReaderFailure.MemoryBudgetClosed> { budget.tryReserve(MemoryKind.DECODED_OUTPUT, 1) }
    }
}
