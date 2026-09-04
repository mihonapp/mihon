package mihon.reader.session

import java.util.concurrent.atomic.AtomicLong

fun interface ReaderGenerationSource {
    fun nextGeneration(): Long
}

class AtomicReaderGenerationSource(
    initialGeneration: Long = 0L,
) : ReaderGenerationSource {
    private val next = AtomicLong(initialGeneration)

    init {
        require(initialGeneration >= 0L) { "initialGeneration must not be negative" }
    }

    override fun nextGeneration(): Long = next.getAndUpdate { current ->
        check(current != Long.MAX_VALUE) { "reader generation overflow" }
        current + 1L
    }
}
