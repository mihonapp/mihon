package mihon.desktop.reader

import mihon.desktop.library.reader.ReaderOnlineChapter
import mihon.desktop.library.reader.ReaderOnlineChapterCatalog
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import java.nio.file.Files
import java.nio.file.Path

internal const val ONLINE_CHAPTER_ASSET_KIND = "ONLINE"

/**
 * Exposes local downloads first and falls back to online chapters from the library database.
 *
 * A chapter row with a missing local file is still treated as local for diagnostics when no online
 * source metadata exists, but an available online source can take over so the user is not blocked
 * by a stale local asset row.
 */
class DesktopReaderCatalog(
    private val local: ReaderChapterCatalog,
    private val online: ReaderOnlineChapterCatalog?,
    private val onlineStorageRoot: Path,
) : ReaderChapterCatalog {
    override fun chapterAsset(chapterId: Long): ReaderChapterAsset? {
        val localAsset = local.chapterAsset(chapterId)
        if (localAsset != null && localAsset.hasReadableContent()) return localAsset
        val onlineAsset = online?.onlineChapter(chapterId)?.toAsset(onlineStorageRoot)
        return onlineAsset ?: localAsset
    }

    override fun adjacentReadableChapter(
        chapterId: Long,
        direction: ChapterDirection,
    ): ReaderChapterAsset? {
        val adjacentOnline = online?.adjacentOnlineChapter(chapterId, direction)
        if (adjacentOnline != null) {
            val adjacentLocal = local.chapterAsset(adjacentOnline.chapterId)
            if (adjacentLocal != null && adjacentLocal.hasReadableContent()) return adjacentLocal
            return adjacentOnline.toAsset(onlineStorageRoot)
        }
        return local.adjacentReadableChapter(chapterId, direction)
    }
}

internal fun ReaderChapterAsset.hasReadableContent(): Boolean {
    val path = storageRoot.resolve(relativePath)
    return if (assetKind == "DIRECTORY") {
        Files.isDirectory(path)
    } else {
        Files.isRegularFile(path)
    }
}

internal fun ReaderOnlineChapter.toAsset(storageRoot: Path): ReaderChapterAsset = ReaderChapterAsset(
    mangaId = mangaId,
    chapterId = chapterId,
    mangaTitle = mangaTitle,
    chapterName = chapterName,
    storageRoot = storageRoot,
    relativePath = Path.of("online", chapterId.toString()),
    assetKind = ONLINE_CHAPTER_ASSET_KIND,
    sizeBytes = 0L,
    modifiedAt = 0L,
    lastPageRead = lastPageRead,
    read = read,
)
