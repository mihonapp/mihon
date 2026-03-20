package tachiyomi.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * A single-threaded dispatcher backed by the IO thread pool.
 * Allows yielding while still putting the SQLite interaction off-thread.
 *
 * This still keeps serialization, keeps the calling thread unblocked,
 * but doesn't suffer from lock contention.
 */
private val transactionDispatcher = Dispatchers.IO.limitedParallelism(1)

/**
 * Returns the transaction dispatcher if we are on a transaction, or the database dispatcher.
 */
internal suspend fun AndroidDatabaseHandler.getCurrentDatabaseContext(): CoroutineContext {
    return coroutineContext[TransactionElement]?.transactionDispatcher ?: queryDispatcher
}

/**
 * Calls the specified suspending [block] in a database transaction. The transaction will be
 * marked as successful unless an exception is thrown in the suspending [block] or the coroutine
 * is cancelled.
 *
 * Transactions are serialized by dispatching onto a [Dispatchers.IO] dispatcher limited to a
 * single thread of parallelism. Transactions are queued and executed on
 * a first-come, first-serve basis.
 *
 * Performing blocking database operations is not permitted in a coroutine scope other than the
 * one received by the suspending block. It is recommended that all Dao functions invoked within
 * the [block] be suspending functions.
 */
internal suspend fun <T> AndroidDatabaseHandler.withTransaction(block: suspend () -> T): T {
    val transactionElement = coroutineContext[TransactionElement]

    // If we are already inside a transaction, just run the block directly — we are already
    // on the correct thread and re-dispatching would deadlock the single-slot dispatcher.
    if (transactionElement != null) {
        return block()
    }

    return withContext(transactionDispatcher) {
        val element = TransactionElement(transactionDispatcher)
        // Build a context that carries TransactionElement (so nested calls detect the active
        // transaction) but has no dispatcher, so runBlocking's own event loop drives execution
        // on the current thread without re-dispatching to the (now-blocked) transactionDispatcher.
        val blockingCtx = coroutineContext.minusKey(ContinuationInterceptor) + element
        db.transactionWithResult {
            runBlocking(blockingCtx) {
                block()
            }
        }
    }
}

/**
 * A [CoroutineContext.Element] that indicates there is an on-going database transaction and
 * carries the dispatcher that should be used for nested DB operations.
 */
private class TransactionElement(
    val transactionDispatcher: CoroutineDispatcher,
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<TransactionElement>

    override val key: CoroutineContext.Key<TransactionElement>
        get() = TransactionElement
}
