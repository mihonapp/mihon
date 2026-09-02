package mihon.reader.cache

/**
 * Snapshot of weighted tile cache counters.
 *
 * Plan invariant: metrics must never contain file paths, page entry names, or manga/chapter
 * titles; every field is an anonymous aggregate counter so a diagnostic dump cannot leak the
 * user's library contents.
 */
data class CacheMetrics(
    val residentBytes: Long,
    val pinnedBytes: Long,
    val entryCount: Int,
    val hitCount: Long,
    val missCount: Long,
    val loadCount: Long,
    val evictionCount: Long,
    val highWaterBytes: Long,
) {
    init {
        require(residentBytes >= 0) { "residentBytes must not be negative" }
        require(pinnedBytes >= 0) { "pinnedBytes must not be negative" }
        require(entryCount >= 0) { "entryCount must not be negative" }
        require(hitCount >= 0) { "hitCount must not be negative" }
        require(missCount >= 0) { "missCount must not be negative" }
        require(loadCount >= 0) { "loadCount must not be negative" }
        require(evictionCount >= 0) { "evictionCount must not be negative" }
        require(highWaterBytes >= 0) { "highWaterBytes must not be negative" }
    }
}
