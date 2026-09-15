package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    suspend fun invalidate(pageId: PageId) = withContext(Dispatchers.IO) {
        require(pageId.chapterId == asset.chapterId.toString())
        val index = Regex("page_(\\d+)\\.[a-z0-9]+").matchEntire(pageId.entryName)
            ?.groupValues?.get(1)?.toIntOrNull() ?: throw ReaderFailure.PageNotFound(pageId.entryName)
        val file = cacheFile(index)
        cacheLock(file).withLock { java.nio.file.Files.deleteIfExists(file.toPath()) }
    }
    private var entries: List<SourceEntry> = emptyList()

    override suspend fun pages(): List<PageDescriptor> = withContext(Dispatchers.IO) {
        checkOpenAndCancellation()
        val list = (cachedPages ?: sourceManager.getPageList(sourceId, chapter))
            .mapIndexed { index, page -> page.copy(index = index) }.also { cachedPages = it }
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

        // Check local file cache for this page
        val cachedFile = cacheFile(page.index)
        val imageBytes = cacheLock(cachedFile).withLock {
            val cached = if (cachedFile.isFile) {
                try {
                    require(cachedFile.length() in 1..MAX_IMAGE_BYTES) { "Invalid cached image size" }
                    cachedFile.readBytes().also { validateImageResponse(it) }
                } catch (_: Exception) {
                    java.nio.file.Files.deleteIfExists(cachedFile.toPath())
                    null
                }
            } else {
                null
            }
            if (cached != null) return@withLock cached
            val bytes = try {
                val visible = kotlinx.coroutines.currentCoroutineContext()[mihon.reader.prefetch.PageLoadPriority]
                    ?.isVisible?.invoke() ?: true
                downloadSourcePage(
                    sourceId,
                    page,
                    chapter.url,
                    networkHelper,
                    sourceManager,
                    priority = if (visible) {
                        mihon.extension.ipc.RequestPriority.READER
                    } else {
                        mihon.extension.ipc.RequestPriority.NORMAL
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ReaderFailure) {
                throw e
            } catch (e: Exception) {
                throw ReaderFailure.RemoteImage(e.message ?: "network request failed", e)
            }
            validateImageResponse(bytes)
            try {
                cachedFile.parentFile?.mkdirs()
                val temporary = java.nio.file.Files.createTempFile(cacheDir.toPath(), "page-", ".tmp")
                try {
                    java.nio.file.Files.write(temporary, bytes)
                    java.nio.file.Files.move(
                        temporary,
                        cachedFile.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                } finally {
                    java.nio.file.Files.deleteIfExists(temporary)
                }
            } catch (_: Exception) {}
            bytes
        }

        validateImageResponse(imageBytes)

        boundedInput(ByteArrayInputStream(imageBytes), imageBytes.size.toLong())
    }

    private fun cacheFile(index: Int): File {
        val identity = "$sourceId\n${chapter.url}"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "online_${digest}_$index.img")
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        private val cacheLocks = Array(64) { Mutex() }
        private fun cacheLock(file: File): Mutex = cacheLocks[
            (file.absolutePath.hashCode() and Int.MAX_VALUE) %
                cacheLocks.size,
        ]
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
