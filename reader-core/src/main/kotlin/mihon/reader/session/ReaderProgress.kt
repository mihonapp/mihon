package mihon.reader.session

import mihon.reader.source.ProgressWriteResult

data class ReaderProgressUpdate(
    val chapterId: String,
    val pageIndex: Long,
    val generation: Long,
    val sequence: Long,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        require(generation >= 0) { "generation must not be negative" }
        require(sequence >= 0) { "sequence must not be negative" }
    }
}

fun interface ReaderProgressSink {
    suspend fun write(update: ReaderProgressUpdate): ProgressWriteResult
}
