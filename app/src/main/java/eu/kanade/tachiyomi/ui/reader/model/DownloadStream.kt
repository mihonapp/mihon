package eu.kanade.tachiyomi.ui.reader.model

import java.io.IOException
import java.io.InputStream

/**
 * A download's bytes as they arrive, for one reader. Past [MAX_BYTES], or once that reader is
 * closed, it stops keeping them; [append] never throws, so the download itself carries on.
 */
class DownloadStream {
    private val lock = Object()
    private var data = ByteArray(1 shl 16)
    private var size = 0
    private var done = false
    private var error: Throwable? = null

    /** Presizes for [bytes], e.g. Content-Length; ignored if implausible. */
    fun reserve(bytes: Long) {
        synchronized(lock) {
            if (error == null && bytes > data.size && bytes <= MAX_RESERVE) {
                try {
                    data = data.copyOf(bytes.toInt())
                } catch (e: OutOfMemoryError) {
                    // Only a hint.
                }
            }
        }
    }

    fun append(src: ByteArray, offset: Int, length: Int) {
        synchronized(lock) {
            if (error != null || done) return
            val needed = size.toLong() + length
            if (needed > MAX_BYTES) {
                // Too big to decode while downloading; the whole-file decode takes over.
                drop("Download too large to stream")
                return
            }
            if (needed > data.size) {
                data = try {
                    data.copyOf(maxOf(needed, minOf(data.size.toLong() * 2, MAX_BYTES)).toInt())
                } catch (e: OutOfMemoryError) {
                    // As too large: the download mustn't fail over this copy.
                    drop("Out of memory buffering the download")
                    return
                }
            }
            System.arraycopy(src, offset, data, size, length)
            size += length
            lock.notifyAll()
        }
    }

    /** Ends the bytes; readers throw [error] if given. */
    fun finish(error: Throwable? = null) {
        synchronized(lock) {
            done = true
            if (this.error == null) this.error = error
            lock.notifyAll()
        }
    }

    /** From the start; [Reader.read] blocks for bytes. Closing it drops what's kept. */
    fun reader(): Reader = Reader()

    // Rather than hold a second copy of the page.
    private fun drop(why: String) {
        if (error == null) error = IOException(why)
        data = ByteArray(0)
        size = 0
        lock.notifyAll()
    }

    private companion object {
        // As the decoder's own input limit.
        const val MAX_BYTES = 1L shl 30
        const val MAX_RESERVE = 64L shl 20
    }

    inner class Reader : InputStream() {
        private var position = 0

        /** All read; throws if the download failed. */
        val ended: Boolean
            get() = synchronized(lock) {
                checkError()
                done && position >= size
            }

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            synchronized(lock) {
                if (len == 0) return 0
                while (position >= size && !done && error == null) lock.wait()
                checkError()
                if (position >= size) return -1
                val n = minOf(len, size - position)
                System.arraycopy(data, position, b, off, n)
                position += n
                return n
            }
        }

        override fun available(): Int = synchronized(lock) { if (error != null) 0 else size - position }

        /** Waits up to [timeoutMs] for [min] unread bytes or the end. */
        fun await(min: Int, timeoutMs: Long) {
            synchronized(lock) {
                val until = System.nanoTime() + timeoutMs * 1_000_000
                while (size - position < min && !done && error == null) {
                    val left = (until - System.nanoTime()) / 1_000_000
                    if (left <= 0) return
                    lock.wait(left)
                }
            }
        }

        override fun close() {
            synchronized(lock) { drop("No longer read") }
        }

        private fun checkError() {
            error?.let { throw IOException("Download failed", it) }
        }
    }
}
