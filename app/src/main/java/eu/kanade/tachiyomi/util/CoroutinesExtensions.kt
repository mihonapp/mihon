package eu.kanade.tachiyomi.util

import kotlinx.coroutines.*

fun launchUI(block: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT, block)

fun launchNow(block: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, block)
