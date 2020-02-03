package eu.kanade.tachiyomi.util.lang

import kotlinx.coroutines.*

fun launchUI(block: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT, block)

@UseExperimental(ExperimentalCoroutinesApi::class)
fun launchNow(block: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, block)
