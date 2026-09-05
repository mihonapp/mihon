package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterExpansionBudget
import mihon.reader.source.ChapterSource
import mihon.reader.source.ManagedChapterSource
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import mihon.reader.source.SourceEntry
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path

open class OnlineChapterSource(
    asset: ReaderChapterAsset,
    private val sourceId: Long,
    private val chapter: SChapter,
    private val processManager: WindowsExtensionProcessManager,
    private val networkHelper: DesktopNetworkHelper,
    private val cacheDir: File,
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
) : ManagedChapterSource(asset, expansionBudget) {

    internal var cachedPages: List<Page>? = null
    private var entries: List<SourceEntry> = emptyList()

    override suspend fun pages(): List<PageDescriptor> = withContext(Dispatchers.IO) {
        checkOpenAndCancellation()
        val list = cachedPages ?: processManager.getPageList(sourceId, chapter).also { cachedPages = it }
        entries = list.map { page ->
            val name = "page_%04d.jpg".format(page.index)
            SourceEntry(name, name, 0L)
        }

        entries.map { entry ->
            PageDescriptor(
                id = PageId(asset.chapterId.toString(), entry.logicalName),
                width = 1,
                height = 1,
            )
        }
    }

    override suspend fun open(pageId: PageId): BoundedPageInput = withContext(Dispatchers.IO) {
        checkOpenAndCancellation()
        pages() // Ensure loaded
        val entry = requireEntry(pageId, entries)
        val pageIndex = entries.indexOf(entry)
        val page = cachedPages?.getOrNull(pageIndex) ?: throw ReaderFailure.PageNotFound(pageId.entryName)

        val imageUrl = page.imageUrl ?: page.url
        if (imageUrl.isBlank()) {
            throw ReaderFailure.PageNotFound("Blank image URL for ${pageId.entryName}")
        }

        // Check local file cache for this page
        val cacheKey = "online_${sourceId}_${chapter.url.hashCode()}_${page.index}.img"
        val cachedFile = File(cacheDir, cacheKey)
        val imageBytes = if (cachedFile.exists() && cachedFile.length() > 0L) {
            cachedFile.readBytes()
        } else {
            // Fetch through brokered network helper
            val req = BrokerHttpRequest(
                method = "GET",
                url = imageUrl,
                headers = mapOf("Referer" to chapter.url),
            )
            val bytes = try {
                networkHelper.downloadRawBytes(req)
            } catch (e: Exception) {
                throw ReaderFailure.UnsupportedFormat("Failed to download page image: ${e.message}")
            }
            try {
                cachedFile.parentFile?.mkdirs()
                cachedFile.writeBytes(bytes)
            } catch (_: Exception) {}
            bytes
        }

        boundedInput(ByteArrayInputStream(imageBytes), imageBytes.size.toLong())
    }

    override fun close() {
        // No-op
    }
}
