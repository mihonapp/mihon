package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(file: File) : PageLoader() {

    private val context: Application by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    init {
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            FileInputStream(file).use { it.copyTo(byteArrayOutputStream) }

            ZipFile(SeekableInMemoryByteChannel(byteArrayOutputStream.toByteArray())).use { zip ->
                zip.entries.asSequence()
                    .filterNot { it.isDirectory }
                    .forEach { entry ->
                        File(tmpDir, entry.name.substringAfterLast("/"))
                            .also { it.createNewFile() }
                            .outputStream().use { pageOutputStream ->
                                zip.getInputStream(entry).copyTo(pageOutputStream)
                                pageOutputStream.flush()
                            }
                    }
            }
        }
    }

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return DirectoryPageLoader(tmpDir).getPages()
    }

    override fun recycle() {
        super.recycle()
        tmpDir.deleteRecursively()
    }
}
