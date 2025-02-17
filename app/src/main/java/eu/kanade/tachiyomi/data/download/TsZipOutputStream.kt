package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.data.download.model.PageData
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Threadsafe wrapper around ZipOutputStream
class TsZipOutputStream(os: OutputStream) : AutoCloseable {

    private val _zos = ZipOutputStream(os)

    @get:Synchronized
    var size = 0
        private set

    fun write(pd: PageData) {
        synchronized(this) {
            _zos.putNextEntry(ZipEntry(pd.filename))
            _zos.write(pd.data)
            _zos.closeEntry()
            size++
        }
    }

    override fun close() {
        synchronized(this) {
            _zos.close()
        }
    }
}
