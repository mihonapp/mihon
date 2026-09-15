package android.os

import mihon.extension.host.ExtensionExecutionContext
import mihon.extension.host.WebViewBridge
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class Looper private constructor() {
    companion object {
        private val main = Looper()

        @JvmStatic fun getMainLooper(): Looper = main

        @JvmStatic fun myLooper(): Looper = main
    }
}

class Handler @JvmOverloads constructor(val looper: Looper = Looper.getMainLooper()) {
    private val jobs = java.util.concurrent.ConcurrentHashMap<Runnable, ScheduledFuture<*>>()
    fun post(runnable: Runnable): Boolean = postDelayed(runnable, 0)
    fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
        val identity = WebViewBridge.identity()
        jobs[runnable] = executor.schedule({
            try {
                ExtensionExecutionContext.duringConstruction(identity) { runnable.run() }
            } finally {
                jobs.remove(runnable)
            }
        }, delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        return true
    }
    fun removeCallbacks(runnable: Runnable) {
        jobs.remove(runnable)?.cancel(false)
    }
    fun removeCallbacksAndMessages(token: Any?) {
        jobs.values.forEach { it.cancel(false) }
        jobs.clear()
    }
    companion object {
        private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "android-main-compat").apply {
                isDaemon =
                    true
            }
        }
    }
}
