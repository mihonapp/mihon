package eu.kanade.presentation.more.settings.screen.debug

import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

class WorkerCancellationTest {

    private val workManager = mockk<WorkManager>()
    private val id = UUID.randomUUID()

    @ParameterizedTest
    @EnumSource(WorkInfo.State::class, names = ["RUNNING", "ENQUEUED", "BLOCKED"])
    fun `cancels only the selected active work by id`(state: WorkInfo.State) = runTest {
        givenState(state)
        val operation = mockk<Operation>()
        every { workManager.cancelWorkById(id) } returns operation
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)

        assertTrue(cancelActiveWorker(workManager, id))

        verify(exactly = 1) { workManager.cancelWorkById(id) }
        verify(exactly = 0) { workManager.cancelAllWork() }
        verify(exactly = 0) { workManager.cancelAllWorkByTag(any()) }
    }

    @ParameterizedTest
    @EnumSource(WorkInfo.State::class, names = ["SUCCEEDED", "FAILED", "CANCELLED"])
    fun `does not cancel work that finished while its menu was open`(state: WorkInfo.State) = runTest {
        givenState(state)

        assertFalse(cancelActiveWorker(workManager, id))

        verify(exactly = 0) { workManager.cancelWorkById(any()) }
    }

    @Test
    fun `does not cancel a removed work record`() = runTest {
        every { workManager.getWorkInfoById(id) } returns Futures.immediateFuture(null)

        assertFalse(cancelActiveWorker(workManager, id))

        verify(exactly = 0) { workManager.cancelWorkById(any()) }
    }

    @Test
    fun `waits for WorkManager to accept cancellation`() = runTest {
        givenState(WorkInfo.State.RUNNING)
        val operation = mockk<Operation>()
        val result = SettableFuture.create<Operation.State.SUCCESS>()
        every { workManager.cancelWorkById(id) } returns operation
        every { operation.result } returns result

        val cancellation = async { cancelActiveWorker(workManager, id) }
        runCurrent()
        assertFalse(cancellation.isCompleted)

        result.set(Operation.SUCCESS)
        assertTrue(cancellation.await())
    }

    @Test
    fun `cancellation errors reach the caller instead of reporting success`() = runTest {
        givenState(WorkInfo.State.RUNNING)
        val operation = mockk<Operation>()
        every { workManager.cancelWorkById(id) } returns operation
        every { operation.result } returns Futures.immediateFailedFuture(IllegalStateException("Cancellation failed"))

        assertThrows<IllegalStateException> { cancelActiveWorker(workManager, id) }
    }

    @Test
    fun `coroutine cancellation propagates without requesting work cancellation`() = runTest {
        every { workManager.getWorkInfoById(id) } throws CancellationException()

        assertThrows<CancellationException> { cancelActiveWorker(workManager, id) }

        verify(exactly = 0) { workManager.cancelWorkById(any()) }
    }

    private fun givenState(state: WorkInfo.State) {
        val workInfo = mockk<WorkInfo>()
        every { workInfo.state } returns state
        every { workManager.getWorkInfoById(id) } returns Futures.immediateFuture(workInfo)
    }
}
