package mihon.reader.session

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressDebouncerTest {
    @Test
    fun `movement writes only the exact latest snapshot after 750 milliseconds`() = runTest {
        val writes = mutableListOf<ReaderProgressUpdate>()
        val debouncer = ProgressDebouncer(backgroundScope) { writes += it }
        val first = progress(sequence = 0, page = 1)
        val latest = progress(sequence = 1, page = 2)

        debouncer.submit(first)
        advanceTimeBy(500)
        debouncer.submit(latest)
        advanceTimeBy(749)
        runCurrent()
        writes.shouldContainExactly()

        advanceTimeBy(1)
        runCurrent()
        writes.shouldContainExactly(latest)
    }

    private fun progress(sequence: Long, page: Long) = ReaderProgressUpdate(
        chapterId = 1,
        pageIndex = page,
        completed = false,
        lastReadEpochMillis = 1,
        readDurationDeltaMillis = 0,
        generation = 1,
        sequence = sequence,
    )
}
