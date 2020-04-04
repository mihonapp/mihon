package exh.util

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

@FlowPreview
fun <T> Flow<T>.cancellable() = onEach {
    coroutineContext.ensureActive()
}
