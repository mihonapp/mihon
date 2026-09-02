package mihon.reader.source

class ChapterExpansionBudget(
    val limitBytes: Long = ReaderLimits.MAX_CHAPTER_EXPANDED_BYTES,
) {
    init {
        require(limitBytes >= 0) { "limitBytes must not be negative" }
    }

    private var chargedBytes: Long = 0

    val usedBytes: Long
        @Synchronized get() = chargedBytes

    @Synchronized
    fun charge(byteCount: Long) {
        require(byteCount >= 0) { "byteCount must not be negative" }
        if (byteCount > limitBytes - chargedBytes) {
            val actual = if (Long.MAX_VALUE - chargedBytes < byteCount) Long.MAX_VALUE else chargedBytes + byteCount
            chargedBytes = limitBytes
            throw ReaderFailure.LimitExceeded("chapter expanded bytes", limitBytes, actual)
        }
        chargedBytes += byteCount
    }
}
