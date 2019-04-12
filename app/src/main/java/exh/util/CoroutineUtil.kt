package exh.util

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.coroutineContext

@FlowPreview
fun <T> Flow<T>.cancellable() = onEach {
    coroutineContext.ensureActive()
}