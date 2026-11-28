package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import mihon.core.archive.ArchiveReader
import tachiyomi.source.local.io.getImageEntryNames

/**
 * Loader used to load a chapter from an archive file.
 */
internal class ArchivePageLoader(
    private val reader: ArchiveReader,
    private val folder: String? = null,
) : PageLoader() {
    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> = reader.getImageEntryNames()
        .filter { folder == null || it.substringBeforeLast('/', missingDelimiterValue = "") == folder }
        .sortedWith(String::compareToCaseInsensitiveNaturalOrder)
        .mapIndexed { i, name ->
            ReaderPage(i).apply {
                stream = { reader.getInputStream(name)!! }
                status = Page.State.Ready
            }
        }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
