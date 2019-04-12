package exh.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

typealias LoadingHandle = String

/**
 * Class used to manage loader UIs
 */
class LoaderManager(parentContext: CoroutineContext = EmptyCoroutineContext): CoroutineScope {
    override val coroutineContext = Dispatchers.Main + parentContext

    private val openLoadingHandles = mutableListOf<LoadingHandle>()
    var loadingChangeListener: (suspend (newIsLoading: Boolean) -> Unit)? = null

    fun openProgressBar(): LoadingHandle {
        val (handle, shouldUpdateLoadingStatus) = synchronized(this) {
            val handle = UUID.randomUUID().toString()
            openLoadingHandles += handle
            handle to (openLoadingHandles.size == 1)
        }

        if(shouldUpdateLoadingStatus) {
            launch {
                updateLoadingStatus(true)
            }
        }

        return handle
    }

    @Synchronized
    fun closeProgressBar(handle: LoadingHandle?) {
        if(handle == null) return

        val shouldUpdateLoadingStatus = synchronized(this) {
            openLoadingHandles.remove(handle) && openLoadingHandles.isEmpty()
        }

        if(shouldUpdateLoadingStatus) {
            launch {
                updateLoadingStatus(false)
            }
        }
    }

    private suspend fun updateLoadingStatus(newStatus: Boolean) {
        loadingChangeListener?.invoke(newStatus)
    }
}
