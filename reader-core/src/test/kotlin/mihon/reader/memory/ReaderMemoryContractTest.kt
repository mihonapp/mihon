package mihon.reader.memory

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class ReaderMemoryContractTest {
    @Test
    fun `memory metrics reject usage that exceeds the limit without long overflow`() {
        shouldThrow<IllegalArgumentException> {
            ReaderMemoryMetrics(limitBytes = Long.MAX_VALUE, reservedBytes = Long.MAX_VALUE, cacheBytes = 1)
        }
    }
}
