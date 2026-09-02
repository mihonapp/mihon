package mihon.reader.source

import java.nio.file.Path

data class ReaderChapterAsset(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val storageRoot: Path,
    val relativePath: Path,
    val assetKind: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val lastPageRead: Long,
    val read: Boolean,
) {
    init {
        require(mangaId >= 0) { "mangaId must not be negative" }
        require(chapterId >= 0) { "chapterId must not be negative" }
        require(mangaTitle.isNotBlank()) { "mangaTitle must not be blank" }
        require(chapterName.isNotBlank()) { "chapterName must not be blank" }
        require(assetKind.isNotBlank()) { "assetKind must not be blank" }
        require(sizeBytes >= 0) { "sizeBytes must not be negative" }
        require(modifiedAt >= 0) { "modifiedAt must not be negative" }
        require(lastPageRead >= 0) { "lastPageRead must not be negative" }
    }
}

enum class ChapterDirection {
    PREVIOUS,
    NEXT,
}

interface ReaderChapterCatalog {
    fun chapterAsset(chapterId: Long): ReaderChapterAsset?

    fun adjacentReadableChapter(chapterId: Long, direction: ChapterDirection): ReaderChapterAsset?
}
