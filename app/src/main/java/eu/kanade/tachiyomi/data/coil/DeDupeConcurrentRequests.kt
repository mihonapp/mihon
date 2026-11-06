package eu.kanade.tachiyomi.data.coil

import kotlinx.coroutines.CompletableDeferred

/**
 * De-duplicates concurrent requests for the same key, like Coil's `DeDupeConcurrentRequestStrategy` but for any
 * result, so it works for decoded images as well as fetches.
 *
 * The first caller runs `block`. If it succeeds, the waiting callers run `block` too, for example to read what the
 * first one cached. If it fails or is canceled, one of them runs it instead.
 *
 * @param isSuccess whether a result counts as a success, for results that report failure without throwing.
 */
class DeDupeConcurrentRequests<T>(
    private val isSuccess: (T) -> Boolean = { true },
) {
    private val requests = mutableMapOf<Any, CompletableDeferred<Boolean>>()

    suspend fun apply(key: Any, block: suspend () -> T): T {
        while (true) {
            var isLeader = false
            val request = synchronized(requests) {
                requests.getOrPut(key) {
                    isLeader = true
                    CompletableDeferred()
                }
            }

            if (!isLeader) {
                if (request.await()) return block()
                continue
            }

            var succeeded = false
            try {
                return block().also { succeeded = isSuccess(it) }
            } finally {
                synchronized(requests) {
                    requests -= key
                }
                request.complete(succeeded)
            }
        }
    }
}
