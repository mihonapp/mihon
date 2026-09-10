package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.reader.image.ImageFormatDetector
import mihon.reader.image.ReaderImageFormat
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
    private val sourceManager: DesktopSourceManager,
    private val networkHelper: DesktopNetworkHelper,
    private val cacheDir: File,
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
) : ManagedChapterSource(asset, expansionBudget) {

    constructor(
        asset: ReaderChapterAsset,
        sourceId: Long,
        chapter: SChapter,
        processManager: WindowsExtensionProcessManager,
        networkHelper: DesktopNetworkHelper,
        cacheDir: File,
        expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
    ) : this(
        asset = asset,
        sourceId = sourceId,
        chapter = chapter,
        sourceManager = DesktopSourceManager(processManager = processManager),
        networkHelper = networkHelper,
        cacheDir = cacheDir,
        expansionBudget = expansionBudget,
    )

    internal var cachedPages: List<Page>? = null
    private var entries: List<SourceEntry> = emptyList()

    override suspend fun pages(): List<PageDescriptor> = withContext(Dispatchers.IO) {
        checkOpenAndCancellation()
        val list = cachedPages ?: sourceManager.getPageList(sourceId, chapter).also { cachedPages = it }
        entries = list.map { page ->
            val extension = page.imageUrl.orEmpty().substringBefore('?').substringAfterLast('.', "img")
                .lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "img"
            val name = "page_%04d.%s".format(page.index, extension)
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
                headers = mapOf("Referer" to chapter.url) + page.headers,
            )
            val bytes = try {
                networkHelper.downloadRawBytes(req)
            } catch (e: Exception) {
                throw ReaderFailure.RemoteImage(e.message ?: "network request failed", e)
            }
            validateImageResponse(bytes)
            try {
                cachedFile.parentFile?.mkdirs()
                cachedFile.writeBytes(bytes)
            } catch (_: Exception) {}
            bytes
        }

        validateImageResponse(imageBytes)

        boundedInput(ByteArrayInputStream(imageBytes), imageBytes.size.toLong())
    }

    override fun close() {
        // No-op
    }

    private fun validateImageResponse(bytes: ByteArray) {
        if (bytes.isEmpty()) throw ReaderFailure.CorruptImage()
        if (ImageFormatDetector.detect(bytes) == ReaderImageFormat.UNKNOWN) {
            val preview = bytes.take(256).toByteArray().decodeToString().trimStart().lowercase()
            val reason = if (preview.startsWith("<") || preview.startsWith("{") || preview.startsWith("[")) {
                "server returned a web/error document instead of image data"
            } else {
                "unrecognized image signature"
            }
            throw ReaderFailure.UnsupportedImage(reason)
        }
    }
}
