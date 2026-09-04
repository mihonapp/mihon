package mihon.reader.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes durable movement updates while keeping only the latest position during a gesture. */
class ProgressDebouncer(
    private val scope: CoroutineScope,
    private val delayMillis: Long = MOVEMENT_DELAY_MILLIS,
    private val write: suspend (ReaderProgressUpdate) -> Unit,
) {
    private val lock = Mutex()
    private var pending: ReaderProgressUpdate? = null
    private var scheduled: Job? = null
    private var closed = false

    init {
        require(delayMillis >= 0L) { "delayMillis must not be negative" }
    }

    suspend fun submit(update: ReaderProgressUpdate) {
        val previous = lock.withLock {
            check(!closed) { "progress debouncer is closed" }
            pending = update
            scheduled.also { scheduled = null }
        }
        previous?.cancel()
        val job = scope.launch {
            delay(delayMillis)
            writeLatest()
        }
        lock.withLock {
            if (closed) job.cancel() else scheduled = job
        }
    }

    suspend fun flush() {
        val job = lock.withLock { scheduled.also { scheduled = null } }
        job?.cancel()
        writeLatest()
    }

    suspend fun closeAndFlush() {
        val job = lock.withLock {
            if (closed) return
            closed = true
            scheduled.also { scheduled = null }
        }
        job?.cancel()
        writeLatest()
    }

    suspend fun cancelWithoutFlush() {
        val job = lock.withLock {
            if (closed) return
            closed = true
            pending = null
            scheduled.also { scheduled = null }
        }
        job?.cancel()
    }

    private suspend fun writeLatest() {
        val update = lock.withLock { pending.also { pending = null } } ?: return
        write(update)
    }

    companion object {
        const val MOVEMENT_DELAY_MILLIS = 750L
    }
}
