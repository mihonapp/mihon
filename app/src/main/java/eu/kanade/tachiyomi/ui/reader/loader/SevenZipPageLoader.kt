package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import tachiyomi.core.util.system.ImageUtil
import java.io.File

/**
 * Loader used to load a chapter from a .7z or .cb7 file.
 */
internal class SevenZipPageLoader(file: File) : PageLoader() {

    private val zip = SevenZFile(file)

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return zip.entries.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = {
                        zip.getInputStream(entry)
                    }
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
