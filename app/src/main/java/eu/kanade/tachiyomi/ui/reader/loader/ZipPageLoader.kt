package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mihon.core.common.extensions.toZipFile
import tachiyomi.core.common.util.system.ImageUtil
import java.nio.channels.SeekableByteChannel

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(channel: SeekableByteChannel) : PageLoader() {

    private val zip = channel.toZipFile()

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return zip.entries.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                val imageBytesDeferred: Deferred<ByteArray> = CoroutineScope(Dispatchers.IO).async {
                    zip.getInputStream(entry).buffered().use { stream ->
                        stream.readBytes()
                    }
                }
                val imageBytes by lazy { runBlocking { imageBytesDeferred.await() } }
                ReaderPage(i).apply {
                    stream = { imageBytes.copyOf().inputStream() }
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
        zip.close()
    }
}
