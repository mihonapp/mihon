package tachiyomi.domain.readinglist.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReadingListAutomaticResolutionUpdateTest {

    @Test
    fun `source unavailable accepts an empty automatic outcome`() {
        val update = ReadingListAutomaticResolutionUpdate(
            state = ReadingListEntryResolutionState.SOURCE_UNAVAILABLE,
            leadingConfidence = null,
            matcherVersion = 1,
            acceptedCandidate = null,
        )

        update.state shouldBe ReadingListEntryResolutionState.SOURCE_UNAVAILABLE
    }

    @Test
    fun `source unavailable rejects a synthetic confidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingListAutomaticResolutionUpdate(
                state = ReadingListEntryResolutionState.SOURCE_UNAVAILABLE,
                leadingConfidence = 10.0,
                matcherVersion = 1,
                acceptedCandidate = null,
            )
        }
    }
}
