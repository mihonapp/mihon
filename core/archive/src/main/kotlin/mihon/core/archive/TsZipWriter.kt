package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.Closeable

class TsZipWriter(val context: Context, file: UniFile) : Closeable {
    private val zw = ZipWriter(context, file)

    @get:Synchronized
    var count = 0
        private set

    fun write(file: UniFile) {
        synchronized(this) {
            zw.write(file)
            count++
        }
    }

    fun write(filename: String, data: ByteArray) {
        synchronized(this) {
            zw.write(filename, data)
            count++
        }
    }

    override fun close() {
        synchronized(this) {
            zw.close()
        }
    }
}
