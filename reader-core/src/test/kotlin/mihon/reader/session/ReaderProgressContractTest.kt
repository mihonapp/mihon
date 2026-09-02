package mihon.reader.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReaderProgressContractTest {
    @Test
    fun `progress update retains every approved storage field at valid boundaries`() {
        ReaderProgressUpdate(
            chapterId = 0,
            pageIndex = 0,
            completed = true,
            lastReadEpochMillis = 0,
            readDurationDeltaMillis = 0,
            generation = 0,
            sequence = 0,
        ) shouldBe ReaderProgressUpdate(
            chapterId = 0,
            pageIndex = 0,
            completed = true,
            lastReadEpochMillis = 0,
            readDurationDeltaMillis = 0,
            generation = 0,
            sequence = 0,
        )
    }

    @Test
    fun `progress update rejects every negative numeric storage field`() {
        val valid = ReaderProgressUpdate(1, 1, false, 1, 1, 1, 1)

        shouldThrow<IllegalArgumentException> { valid.copy(chapterId = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(pageIndex = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(lastReadEpochMillis = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(readDurationDeltaMillis = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(generation = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(sequence = -1) }
    }

    @Test
    fun `progress persistence exposes applied and stale outcomes through suspending record`() = runTest {
        ProgressWriteResult.entries.shouldContainExactly(ProgressWriteResult.APPLIED, ProgressWriteResult.STALE)
        val sink = ReaderProgressSink { update ->
            if (update.sequence == 0L) ProgressWriteResult.APPLIED else ProgressWriteResult.STALE
        }

        sink.record(ReaderProgressUpdate(1, 0, false, 0, 0, 0, 0)) shouldBe ProgressWriteResult.APPLIED
        sink.record(ReaderProgressUpdate(1, 0, true, 1, 1, 0, 1)) shouldBe ProgressWriteResult.STALE
    }
}
