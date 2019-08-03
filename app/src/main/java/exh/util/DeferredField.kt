package exh.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Field that can be initialized later. Users can suspend while waiting for the field to initialize.
 *
 * @author nulldev
 */
class DeferredField<T> {
    @Volatile
    private var content: T? = null

    @Volatile
    var initialized = false
        private set

    private val mutex = Mutex(true)

    /**
     * Initialize the field
     */
    fun initialize(content: T) {
        // Fast-path new listeners
        this.content = content
        initialized = true

        // Notify current listeners
        mutex.unlock()
    }

    /**
     * Will only suspend if !initialized.
     */
    suspend fun get(): T {
        // Check if field is initialized and return immediately if it is
        if(initialized) return content as T

        // Wait for field to initialize
        mutex.withLock {}

        // Field is initialized, return value
        return content as T
    }
}