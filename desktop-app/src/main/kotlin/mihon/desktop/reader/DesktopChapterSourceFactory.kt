package mihon.desktop.reader

import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.OnlineChapterSource
import mihon.desktop.library.reader.ReaderOnlineChapterCatalog
import mihon.extension.source.model.SChapter
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import java.io.File

/** Creates local container sources or streams chapters from their extension source. */
class DesktopChapterSourceFactory(
    private val local: ChapterSourceFactory,
    private val onlineChapters: ReaderOnlineChapterCatalog?,
    private val sourceManager: DesktopSourceManager?,
    private val networkHelper: DesktopNetworkHelper?,
    private val onlineCacheDir: File,
) : ChapterSourceFactory {
    override fun create(asset: ReaderChapterAsset): ChapterSource {
        if (asset.assetKind != ONLINE_CHAPTER_ASSET_KIND) return local.create(asset)

        val chapter = onlineChapters?.onlineChapter(asset.chapterId)
            ?: throw ReaderFailure.UnsupportedFormat("online chapter metadata is unavailable")
        val manager = sourceManager
            ?: throw ReaderFailure.UnsupportedFormat("online source manager is unavailable")
        val network = networkHelper
            ?: throw ReaderFailure.UnsupportedFormat("online network helper is unavailable")

        return OnlineChapterSource(
            asset = asset,
            sourceId = chapter.sourceId,
            chapter = SChapter(
                url = chapter.chapterUrl,
                name = chapter.chapterName,
                dateUpload = chapter.dateUpload,
                chapterNumber = chapter.chapterNumber.toFloat(),
                scanlator = chapter.scanlator,
            ),
            sourceManager = manager,
            networkHelper = network,
            cacheDir = onlineCacheDir,
        )
    }
}
