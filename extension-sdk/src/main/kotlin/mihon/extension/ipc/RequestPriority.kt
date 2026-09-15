package mihon.extension.ipc

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Carries user-visible work priority across source calls without changing source APIs. */
class RequestPriority(val value: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RequestPriority> {
        const val BACKGROUND = 0
        const val NORMAL = 1
        const val READER = 2
    }
}
