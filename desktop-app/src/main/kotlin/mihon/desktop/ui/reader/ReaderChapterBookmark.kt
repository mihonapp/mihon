package mihon.desktop.ui.reader

import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.reader.ReaderChapterBookmarkStore

/**
 * Repository-backed chapter bookmark store.
 *
 * Wire [findChapter] to the active manga's chapter snapshot and [mutationPort] to the library
 * repository so the reader toggle updates the same row used by the library UI. The function-based
 * constructor keeps the store easy to test without a full repository.
 */
class LibraryChapterBookmarkStore(
    private val findChapter: (Long) -> LibraryChapter?,
    private val updateChapter: (ChapterRecord) -> Unit,
) : ReaderChapterBookmarkStore {

    constructor(
        findChapter: (Long) -> LibraryChapter?,
        mutationPort: LibraryMutationPort,
    ) : this(findChapter, mutationPort::updateChapter)

    override fun isBookmarked(chapterId: Long): Boolean = findChapter(chapterId)?.bookmark ?: false

    override fun setBookmarked(chapterId: Long, bookmarked: Boolean) {
        val chapter = findChapter(chapterId) ?: return
        if (chapter.bookmark == bookmarked) return
        updateChapter(chapter.toChapterRecord(bookmarked))
    }
}

fun LibraryChapter.toChapterRecord(bookmark: Boolean = this.bookmark): ChapterRecord = ChapterRecord(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    lastModifiedAt = lastModifiedAt,
    version = version,
    memoJson = memoJson,
)
