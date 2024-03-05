package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.EpubFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.channels.SeekableByteChannel

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(channel: SeekableByteChannel) : PageLoader() {

    private val epub = EpubFile(channel)

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return epub.getImagesFromPages()
            .mapIndexed { i, path ->
                val imageBytesDeferred: Deferred<ByteArray> = CoroutineScope(Dispatchers.IO).async {
                    epub.getInputStream(epub.getEntry(path)!!).buffered().use { stream ->
                        stream.readBytes()
                    }
                }
                val imageBytes by lazy { runBlocking { imageBytesDeferred.await() } }
                ReaderPage(i).apply {
                    stream = { imageBytes.copyOf().inputStream() }
                    status = Page.State.READY
                }
            }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        epub.close()
    }
}
