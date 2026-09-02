package mihon.reader.session

enum class ProgressWriteResult {
    APPLIED,
    STALE,
}

data class ReaderProgressUpdate(
    val chapterId: Long,
    val pageIndex: Long,
    val completed: Boolean,
    val lastReadEpochMillis: Long,
    val readDurationDeltaMillis: Long,
    val generation: Long,
    val sequence: Long,
) {
    init {
        require(chapterId >= 0) { "chapterId must not be negative" }
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        require(lastReadEpochMillis >= 0) { "lastReadEpochMillis must not be negative" }
        require(readDurationDeltaMillis >= 0) { "readDurationDeltaMillis must not be negative" }
        require(generation >= 0) { "generation must not be negative" }
        require(sequence >= 0) { "sequence must not be negative" }
    }
}

fun interface ReaderProgressSink {
    suspend fun record(update: ReaderProgressUpdate): ProgressWriteResult
}
