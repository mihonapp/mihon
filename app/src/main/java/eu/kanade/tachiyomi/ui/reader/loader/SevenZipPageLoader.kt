package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.SevenZUtil.getImages
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File

/**
 * Loader used to load a chapter from a .7z or .cb7 file.
 */
internal class SevenZipPageLoader(file: File) : PageLoader() {

    private val zip by lazy { SevenZFile(file) }

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return zip.getImages()
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = { entry.copyOf().inputStream() }
                    status = Page.State.READY
                }
            }.toList()
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        zip.close()
    }
}
