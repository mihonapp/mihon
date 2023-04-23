package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

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
        ZipInputStream(FileInputStream(file)).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    File(tmpDir, entry.name).also { it.createNewFile() }
                        .outputStream().use { pageOutputStream ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pageOutputStream.write(zipInputStream.readNBytes(entry.size.toInt()))
                            } else {
                                val buffer = ByteArray(2048)
                                var len: Int
                                while (
                                    zipInputStream.read(buffer, 0, buffer.size)
                                        .also { len = it } >= 0
                                ) {
                                    pageOutputStream.write(buffer, 0, len)
                                }
                            }
                            pageOutputStream.flush()
                        }
                    zipInputStream.closeEntry()
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
