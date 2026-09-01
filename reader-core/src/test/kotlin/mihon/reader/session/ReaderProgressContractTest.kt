package mihon.reader.session

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class ReaderProgressContractTest {
    @Test
    fun `progress persistence result belongs to the progress contract`() {
        ProgressWriteResult.entries.shouldContainExactly(
            ProgressWriteResult.WRITTEN,
            ProgressWriteResult.UNCHANGED,
            ProgressWriteResult.REJECTED,
        )
    }
}
