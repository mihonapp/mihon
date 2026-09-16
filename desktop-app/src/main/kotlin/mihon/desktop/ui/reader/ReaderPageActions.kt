package mihon.desktop.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.image.CustomCoverManager
import mihon.desktop.platform.DesktopBrowserHelper
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Everything the page-actions dialog needs to know about the currently selected page. */
data class ReaderPageActionTarget(
    val page: PageDescriptor,
    val pageIndex: Int,
    val pageUrl: String? = null,
    val mangaId: Long? = null,
    val isLocal: Boolean = pageUrl.isNullOrBlank(),
) {
    val fileName: String
        get() = readerPageFileName(page, pageIndex)

    val canOpenInBrowser: Boolean
        get() = !isLocal && !pageUrl.isNullOrBlank()
}

class ReaderPageImage(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String = "image/png",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReaderPageImage) return false
        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * (31 * bytes.contentHashCode() + fileName.hashCode()) + mimeType.hashCode()

    override fun toString(): String = "ReaderPageImage(fileName=$fileName, mimeType=$mimeType, bytes=${bytes.size})"
}

/**
 * Platform edge for page actions. Tests provide an implementation directly; the reader screen
 * supplies [defaultReaderPageActionHandler] when no override is passed.
 */
interface ReaderPageActionHandler {
    suspend fun loadImage(target: ReaderPageActionTarget): ReaderPageImage?

    fun saveImage(target: ReaderPageActionTarget, image: ReaderPageImage): Boolean

    fun copyImage(image: ReaderPageImage): Boolean

    fun shareImage(image: ReaderPageImage): Boolean

    fun setAsCover(target: ReaderPageActionTarget, image: ReaderPageImage): Boolean

    fun openInBrowser(url: String): Boolean
}

/**
 * Keeps the currently displayed page image available to page actions. [DecodedReaderPage] owns the
 * display lease and registers the active frame here for the reader screen.
 */
class ReaderPageImageStore {
    private val images = LinkedHashMap<PageId, ImageBitmap>()

    @Synchronized
    fun put(pageId: PageId, image: ImageBitmap) {
        images[pageId] = image
    }

    @Synchronized
    fun remove(pageId: PageId) {
        images.remove(pageId)
    }

    @Synchronized
    fun get(pageId: PageId): ImageBitmap? = images[pageId]

    @Synchronized
    fun clear() {
        images.clear()
    }

    val size: Int
        @Synchronized get() = images.size
}

val LocalReaderPageImageStore = staticCompositionLocalOf<ReaderPageImageStore?> { null }

fun readerPageFileName(page: PageDescriptor, pageIndex: Int): String {
    val baseName = page.id.entryName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.', page.id.entryName)
        .ifBlank { "page-${pageIndex + 1}" }
    val sanitized = baseName.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("")
    return "${sanitized.ifBlank { "page-${pageIndex + 1}" }}.png"
}

fun ImageBitmap.encodePageImage(target: ReaderPageActionTarget): ReaderPageImage? {
    val bytes = runCatching {
        val bitmap = asSkiaBitmap()
        val image = Image.makeFromBitmap(bitmap)
        try {
            image.encodeToData(EncodedImageFormat.PNG)?.bytes
        } finally {
            image.close()
        }
    }.getOrNull() ?: return null
    return ReaderPageImage(bytes = bytes, fileName = target.fileName, mimeType = "image/png")
}

fun defaultReaderPageActionHandler(
    imageStore: ReaderPageImageStore,
    customCoverManager: CustomCoverManager?,
    saveDialogTitle: String = "Save page image",
): ReaderPageActionHandler = object : ReaderPageActionHandler {
    override suspend fun loadImage(target: ReaderPageActionTarget): ReaderPageImage? =
        withContext(Dispatchers.Default) {
            imageStore.get(target.page.id)?.encodePageImage(target)
        }

    override fun saveImage(target: ReaderPageActionTarget, image: ReaderPageImage): Boolean =
        ReaderPageFileActions.saveWithDialog(image, saveDialogTitle)

    override fun copyImage(image: ReaderPageImage): Boolean =
        ReaderPageClipboard.copyImage(image)

    override fun shareImage(image: ReaderPageImage): Boolean =
        ReaderPageFileActions.shareViaOs(image)

    override fun setAsCover(target: ReaderPageActionTarget, image: ReaderPageImage): Boolean {
        val mangaId = target.mangaId ?: return false
        val manager = customCoverManager ?: return false
        val temp = runCatching { Files.createTempFile("mihon-page-cover-", ".png") }.getOrNull() ?: return false
        return try {
            Files.write(temp, image.bytes)
            runCatching {
                manager.setCustomCover(mangaId, temp)
                true
            }.getOrDefault(false)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
    }

    override fun openInBrowser(url: String): Boolean = DesktopBrowserHelper.openInBrowser(url)
}

private object ReaderPageClipboard {
    fun copyImage(image: ReaderPageImage): Boolean {
        if (GraphicsEnvironment.isHeadless()) return false
        val bufferedImage = runCatching {
            ImageIO.read(ByteArrayInputStream(image.bytes))
        }.getOrNull() ?: return false
        return runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(bufferedImage), null)
            true
        }.getOrDefault(false)
    }

    private class ImageTransferable(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            require(flavor == DataFlavor.imageFlavor) { "Unsupported flavor: $flavor" }
            return image
        }
    }
}

private object ReaderPageFileActions {
    fun saveWithDialog(image: ReaderPageImage, title: String): Boolean {
        if (GraphicsEnvironment.isHeadless()) return false
        return runCatching {
            val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
                file = image.fileName
                isVisible = true
            }
            val fileName = dialog.file ?: return false
            val directory = dialog.directory ?: return false
            Files.write(Path.of(directory, fileName), image.bytes)
            true
        }.getOrDefault(false)
    }

    fun shareViaOs(image: ReaderPageImage): Boolean {
        val temp = runCatching {
            Files.createTempFile("mihon-page-", ".png").also { it.toFile().deleteOnExit() }
        }.getOrNull() ?: return false
        return try {
            Files.write(temp, image.bytes)
            openPathInOs(temp)
        } catch (_: Exception) {
            false
        }
    }

    private fun openPathInOs(path: Path): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        val processResult = runCatching {
            when {
                os.contains("win") -> ProcessBuilder("explorer.exe", "/select,${path.toAbsolutePath()}").start()
                os.contains("mac") -> ProcessBuilder("open", "-R", path.toAbsolutePath().toString()).start()
                else -> ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start()
            }
            true
        }.getOrDefault(false)
        if (processResult) return true
        if (GraphicsEnvironment.isHeadless()) return false
        return runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile())
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
