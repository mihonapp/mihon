package mihon.domain.source.service

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

class SourceResponseTimeoutTest {
    @Test
    fun `slow source can respond before the deadline`() = runTest {
        awaitSourceResponse {
            delay(59_000)
            "pages"
        } shouldBe "pages"
    }

    @Test
    fun `stalled source becomes a recoverable network failure`() = runTest {
        var timedOut = false
        try {
            awaitSourceResponse { awaitCancellation() }
        } catch (_: SocketTimeoutException) {
            timedOut = true
        }
        timedOut shouldBe true
        testScheduler.currentTime shouldBe 60_000L
    }

    @Test
    fun `cancellation still stops downloading without source recovery`() = runTest {
        var canceled = false
        try {
            awaitSourceResponse { throw CancellationException("paused") }
        } catch (_: CancellationException) {
            canceled = true
        }
        canceled shouldBe true
        testScheduler.currentTime shouldBe 0L
    }
}
