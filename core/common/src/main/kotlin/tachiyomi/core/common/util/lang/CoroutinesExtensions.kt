package tachiyomi.core.common.util.lang

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun CoroutineScope.launchUI(block: suspend CoroutineScope.() -> Unit): Job =
    launch(Dispatchers.Main, block = block)

fun CoroutineScope.launchIO(block: suspend CoroutineScope.() -> Unit): Job =
    launch(Dispatchers.IO, block = block)

fun CoroutineScope.launchNonCancellable(block: suspend CoroutineScope.() -> Unit): Job =
    launchIO { withContext(NonCancellable, block) }

suspend fun <T> withUIContext(block: suspend CoroutineScope.() -> T) = withContext(
    Dispatchers.Main,
    block,
)

suspend fun <T> withIOContext(block: suspend CoroutineScope.() -> T) = withContext(
    Dispatchers.IO,
    block,
)

suspend fun <T> withNonCancellableContext(block: suspend CoroutineScope.() -> T) =
    withContext(NonCancellable, block)
