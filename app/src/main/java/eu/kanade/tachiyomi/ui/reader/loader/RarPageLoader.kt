package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
internal class RarPageLoader(file: File) : PageLoader() {

    private val context: Application by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${file.hashCode()}").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    init {
        Archive(file).use { rar ->
            rar.fileHeaders.asSequence()
                .filterNot { it.isDirectory }
                .forEach { header ->
                    val pageFile = File(tmpDir, header.fileName).also { it.createNewFile() }
                    getStream(rar, header).use {
                        it.copyTo(pageFile.outputStream())
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

    /**
     * Returns an input stream for the given [header].
     */
    private fun getStream(rar: Archive, header: FileHeader): InputStream {
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        synchronized(this) {
            try {
                pipeOut.use {
                    rar.extractFile(header, it)
                }
            } catch (e: Exception) {
            }
        }
        return pipeIn
    }
}
