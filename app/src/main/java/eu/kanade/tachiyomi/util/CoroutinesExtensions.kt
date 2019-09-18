package eu.kanade.tachiyomi.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main

fun launchUI(block: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT, block)

fun launchNow(block: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, block)
