package exh.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedField<T>(private val expiresAfterMs: Long) {
    @Volatile
    private var initTime: Long = -1

    @Volatile
    private var content: T? = null

    private val mutex = Mutex()

    suspend fun obtain(producer: suspend () -> T): T {
        return mutex.withLock {
            if(initTime < 0 || System.currentTimeMillis() - initTime > expiresAfterMs) {
                content = producer()
            }

            content as T
        }
    }
}
