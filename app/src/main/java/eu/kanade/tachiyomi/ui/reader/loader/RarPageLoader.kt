package eu.kanade.tachiyomi.ui.reader.loader

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.ImageUtil
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors

/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
class RarPageLoader(file: File) : PageLoader() {

    /**
     * The rar archive to load pages from.
     */
    private val archive = Archive(file)

    /**
     * Pool for copying compressed files to an input stream.
     */
    private val pool = Executors.newFixedThreadPool(1)

    /**
     * Recycles this loader and the open archive.
     */
    override fun recycle() {
        super.recycle()
        archive.close()
        pool.shutdown()
    }

    /**
     * Returns an RxJava Single containing the pages found on this rar archive ordered with a natural
     * comparator.
     */
    override suspend fun getPages(): List<ReaderPage> {
        return archive.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, header ->
                ReaderPage(i).apply {
                    stream = { getStream(header) }
                    status = Page.State.READY
                }
            }
            .toList()
    }

    /**
     * No additional action required to load the page
     */
    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    /**
     * Returns an input stream for the given [header].
     */
    private fun getStream(header: FileHeader): InputStream {
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        pool.execute {
            try {
                pipeOut.use {
                    archive.extractFile(header, it)
                }
            } catch (e: Exception) {
            }
        }
        return pipeIn
    }
}
