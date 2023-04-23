package eu.kanade.tachiyomi.ui.reader.loader

import android.os.Build
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import tachiyomi.core.util.system.ImageUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
internal class ZipPageLoader(file: File) : PageLoader() {

    private val zip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ZipFile(file, StandardCharsets.ISO_8859_1)
    } else {
        ZipFile(file)
    }

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return zip.entries().asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = { zip.getInputStream(entry) }
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
