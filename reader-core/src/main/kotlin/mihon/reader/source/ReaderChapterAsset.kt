package mihon.reader.source

import java.nio.file.Path

data class ReaderChapterAsset(
    val chapterId: String,
    val path: Path,
    val lastPageRead: Long = 0,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(lastPageRead >= 0) { "lastPageRead must not be negative" }
    }
}

enum class ChapterDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}

enum class ProgressWriteResult {
    WRITTEN,
    UNCHANGED,
    REJECTED,
}

interface ReaderChapterCatalog {
    suspend fun chapters(): List<ReaderChapterAsset>
}
