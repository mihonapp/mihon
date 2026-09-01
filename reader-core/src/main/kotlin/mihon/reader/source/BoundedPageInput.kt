package mihon.reader.source

import java.io.Closeable
import java.io.InputStream

class BoundedPageInput(
    val input: InputStream,
    val byteCount: Long,
) : Closeable {
    init {
        require(byteCount >= 0) { "byteCount must not be negative" }
    }

    override fun close() = input.close()
}
