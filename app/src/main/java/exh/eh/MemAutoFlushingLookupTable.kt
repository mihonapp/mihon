package exh.eh

import android.support.v4.util.AtomicFile
import android.util.SparseArray
import com.elvishew.xlog.XLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

/**
 * In memory Int -> Obj lookup table implementation that
 * automatically persists itself to disk atomically and asynchronously.
 *
 * Thread safe
 *
 * @author nulldev
 */
class MemAutoFlushingLookupTable<T>(
        file: File,
        private val serializer: EntrySerializer<T>,
        private val debounceTimeMs: Long = 3000
) : CoroutineScope, Closeable {
    /**
     * The context of this scope.
     * Context is encapsulated by the scope and used for implementation of coroutine builders that are extensions on the scope.
     * Accessing this property in general code is not recommended for any purposes except accessing [Job] instance for advanced usages.
     *
     * By convention, should contain an instance of a [job][Job] to enforce structured concurrency.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + SupervisorJob()

    private val table = SparseArray<T>(INITIAL_SIZE)
    private val mutex = Mutex(true)

    // Used to debounce
    @Volatile
    private var writeCounter = Long.MIN_VALUE
    @Volatile
    private var flushed = true

    private val atomicFile = AtomicFile(file)

    private val shutdownHook = thread(start = false) {
        if(!flushed) writeSynchronously()
    }

    init {
        initialLoad()

        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    private fun InputStream.requireBytes(targetArray: ByteArray, byteCount: Int): Boolean {
        var readIter = 0
        while (true) {
            val readThisIter = read(targetArray, readIter, byteCount - readIter)
            if(readThisIter <= 0) return false // No more data to read
            readIter += readThisIter
            if(readIter == byteCount) return true
        }
    }

    private fun initialLoad() {
        launch {
            try {
                atomicFile.openRead().buffered().use { input ->
                    val bb = ByteBuffer.allocate(8)

                    while(true) {
                        if(!input.requireBytes(bb.array(), 8)) break
                        val k = bb.getInt(0)
                        val size = bb.getInt(4)
                        val strBArr = ByteArray(size)
                        if(!input.requireBytes(strBArr, size)) break
                        table.put(k, serializer.read(strBArr.toString(Charsets.UTF_8)))
                    }
                }
            } catch(e: FileNotFoundException) {
                XLog.d("Lookup table not found!", e)
                // Ignored
            }

            mutex.unlock()
        }
    }

    private fun tryWrite() {
        val id = ++writeCounter
        flushed = false
        launch {
            delay(debounceTimeMs)
            if(id != writeCounter) return@launch

            mutex.withLock {
                // Second check inside of mutex to prevent dupe writes
                if(id != writeCounter) return@launch
                withContext(NonCancellable) {
                    writeSynchronously()

                    // Yes there is a race here, no it's isn't critical
                    if (id == writeCounter) flushed = true
                }
            }
        }
    }

    private fun writeSynchronously() {
        val bb = ByteBuffer.allocate(ENTRY_SIZE_BYTES)

        val fos = atomicFile.startWrite()
        try {
            val out = fos.buffered()
            for(i in 0 until table.size()) {
                val k = table.keyAt(i)
                val v = serializer.write(table.valueAt(i)).toByteArray(Charsets.UTF_8)
                bb.putInt(0, k)
                bb.putInt(4, v.size)
                out.write(bb.array())
                out.write(v)
            }
            out.flush()
            atomicFile.finishWrite(fos)
        } catch(t: Throwable) {
            atomicFile.failWrite(fos)
            throw t
        }
    }

    suspend fun put(key: Int, value: T) {
        mutex.withLock { table.put(key, value) }
        tryWrite()
    }

    suspend fun get(key: Int): T? {
        return mutex.withLock { table.get(key) }
    }

    suspend fun size(): Int {
        return mutex.withLock { table.size() }
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * `try`-with-resources statement.
     *
     *
     * While this interface method is declared to throw `Exception`, implementers are *strongly* encouraged to
     * declare concrete implementations of the `close` method to
     * throw more specific exceptions, or to throw no exception at all
     * if the close operation cannot fail.
     *
     *
     *  Cases where the close operation may fail require careful
     * attention by implementers. It is strongly advised to relinquish
     * the underlying resources and to internally *mark* the
     * resource as closed, prior to throwing the exception. The `close` method is unlikely to be invoked more than once and so
     * this ensures that the resources are released in a timely manner.
     * Furthermore it reduces problems that could arise when the resource
     * wraps, or is wrapped, by another resource.
     *
     *
     * *Implementers of this interface are also strongly advised
     * to not have the `close` method throw [ ].*
     *
     * This exception interacts with a thread's interrupted status,
     * and runtime misbehavior is likely to occur if an `InterruptedException` is [ suppressed][Throwable.addSuppressed].
     *
     * More generally, if it would cause problems for an
     * exception to be suppressed, the `AutoCloseable.close`
     * method should not throw it.
     *
     *
     * Note that unlike the [close][java.io.Closeable.close]
     * method of [java.io.Closeable], this `close` method
     * is *not* required to be idempotent.  In other words,
     * calling this `close` method more than once may have some
     * visible side effect, unlike `Closeable.close` which is
     * required to have no effect if called more than once.
     *
     * However, implementers of this interface are strongly encouraged
     * to make their `close` methods idempotent.
     *
     * @throws Exception if this resource cannot be closed
     */
    override fun close() {
        runBlocking { coroutineContext[Job]?.cancelAndJoin() }
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }

    interface EntrySerializer<T> {
        /**
         * Serialize an entry as a String.
         */
        fun write(entry: T): String

        /**
         * Read an entry from a String.
         */
        fun read(string: String): T
    }

    companion object {
        private const val INITIAL_SIZE = 1000
        private const val ENTRY_SIZE_BYTES = 8
    }
}