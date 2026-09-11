package mihon.desktop.library.reader

import mihon.reader.source.ChapterDirection

/**
 * Metadata needed to open a chapter through its extension source when no local download exists.
 *
 * The reader core only knows about [mihon.reader.source.ReaderChapterAsset]s. [ReaderOnlineChapter]
 * is the persistence-side lookup used by the desktop app to synthesize an online asset and to
 * resolve adjacent chapters without requiring a local download.
 */
data class ReaderOnlineChapter(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val chapterUrl: String,
    val sourceId: Long,
    val chapterNumber: Double,
    val sourceOrder: Long,
    val dateUpload: Long,
    val scanlator: String?,
    val lastPageRead: Long,
    val read: Boolean,
) {
    init {
        require(mangaId >= 0L) { "mangaId must not be negative" }
        require(chapterId >= 0L) { "chapterId must not be negative" }
        require(mangaTitle.isNotBlank()) { "mangaTitle must not be blank" }
        require(chapterName.isNotBlank()) { "chapterName must not be blank" }
        require(chapterUrl.isNotBlank()) { "chapterUrl must not be blank" }
        require(sourceId >= 0L) { "sourceId must not be negative" }
        require(chapterNumber.isFinite()) { "chapterNumber must be finite" }
        require(lastPageRead >= 0L) { "lastPageRead must not be negative" }
    }
}

interface ReaderOnlineChapterCatalog {
    fun onlineChapter(chapterId: Long): ReaderOnlineChapter? = null

    fun adjacentOnlineChapter(chapterId: Long, direction: ChapterDirection): ReaderOnlineChapter? = null
}
