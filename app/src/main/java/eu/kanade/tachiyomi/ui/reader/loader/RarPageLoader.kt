package eu.kanade.tachiyomi.ui.reader.loader

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import tachiyomi.core.util.system.ImageUtil
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
internal class RarPageLoader(file: File) : PageLoader() {

    private val rar = Archive(file)

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return rar.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { rar.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, header ->
                ReaderPage(i).apply {
                    stream = { getStream(rar, header) }
                    status = Page.State.READY
                }
            }
            .toList()
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        rar.close()
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
