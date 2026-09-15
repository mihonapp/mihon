package mihon.reader.prefetch

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Source adapters may distinguish visible demand from speculative page loads. */
class PageLoadPriority(val isVisible: () -> Boolean) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PageLoadPriority>
}
