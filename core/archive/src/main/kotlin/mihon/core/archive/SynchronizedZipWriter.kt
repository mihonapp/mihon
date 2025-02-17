package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.Closeable

class SynchronizedZipWriter(val context: Context, file: UniFile) : Closeable {
    private val delegate = ZipWriter(context, file)

    @get:Synchronized
    var count = 0
        private set

    fun write(file: UniFile) {
        synchronized(this) {
            delegate.write(file)
            count++
        }
    }

    fun write(filename: String, data: ByteArray) {
        synchronized(this) {
            delegate.write(filename, data)
            count++
        }
    }

    override fun close() {
        synchronized(this) {
            delegate.close()
        }
    }
}
