package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.Closeable

class SynchronizedZipWriter(val context: Context, file: UniFile) : Closeable {
    private val delegate = ZipWriter(context, file)

    val files: List<String?>
        get() {
            synchronized(this) {
                return delegate.files
            }
        }

    fun write(file: UniFile) {
        synchronized(this) {
            delegate.write(file)
        }
    }

    fun write(filename: String, data: ByteArray) {
        synchronized(this) {
            delegate.write(filename, data)
        }
    }

    override fun close() {
        synchronized(this) {
            delegate.close()
        }
    }
}
