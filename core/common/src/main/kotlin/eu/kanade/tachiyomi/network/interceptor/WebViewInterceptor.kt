package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.i18n.MR
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

abstract class WebViewInterceptor(
    private val context: Context,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    /**
     * A per-host lock manager that makes sure only one instance of the interceptor is run per host at a time
     */
    private val locks = object {
        private val MAX_CAPACITY = 256

        /**
         * This is a mapping of host to two locks. The first lock is used to synchronize calls, and the second lock is
         * to prevent the entry from being removed while it is used.
         */
        private val data = object : LinkedHashMap<String, Pair<ReentrantReadWriteLock, ReentrantReadWriteLock>>() {
            override fun removeEldestEntry(
                eldest: Map.Entry<String, Pair<ReentrantReadWriteLock, ReentrantReadWriteLock>>,
            ): Boolean {
                if (size > MAX_CAPACITY) {
                    eldest.value.second.writeLock().withLock {
                        if (size > MAX_CAPACITY) {
                            remove(eldest.key)
                        }
                    }
                }
                return false
            }
        }

        /**
         * Runs the block while preventing the entry from being removed, which probably only happens in extreme cases.
         */
        inline fun <T> withLock(host: String, block: (ReentrantReadWriteLock) -> T): T {
            val (lock, entryLock) = synchronized(data) {
                var entry = data[host]
                if (entry == null) {
                    entry = ReentrantReadWriteLock() to ReentrantReadWriteLock()
                    data.put(host, entry)
                }
                entry.first to entry.second.readLock().apply { lock() }
            }
            try {
                return block(lock)
            } finally {
                entryLock.unlock()
            }
        }
    }

    /**
     * When this is called, it initializes the WebView if it wasn't already. We use this to avoid
     * blocking the main thread too much. If used too often we could consider moving it to the
     * Application class.
     */
    private val initWebView by lazy {
        // Crashes on some devices. We skip this in some cases since the only impact is slower
        // WebView init in those rare cases.
        // See https://bugs.chromium.org/p/chromium/issues/detail?id=1279562
        if (DeviceUtil.isMiui || (Build.VERSION.SDK_INT == Build.VERSION_CODES.S && DeviceUtil.isSamsung)) {
            return@lazy
        }

        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Avoid some crashes like when Chrome/WebView is being updated.
        }
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun getNonce(url: HttpUrl): String?

    open fun isBypassed(url: HttpUrl, oldNonce: String?): Boolean = getNonce(url).let {
        !it.isNullOrBlank() && it != oldNonce
    }

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response, nonce: String?): Response?

    @OptIn(DelicateCoroutinesApi::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        return locks.withLock(url.host) { lock ->
            val (response, nonce) = lock.readLock().withLock {
                chain.proceed(request).also {
                    if (!shouldIntercept(it)) {
                        return it
                    }
                } to getNonce(url)
            }

            lock.writeLock().withLock {
                if (isBypassed(url, nonce)) {
                    return@withLock null
                }

                if (!WebViewUtil.supportsWebView(context)) {
                    launchUI {
                        context.toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
                    }
                    return@withLock response
                }
                initWebView

                intercept(chain, request, response, nonce)
            }
        } ?: chain.proceed(request)
    }

    fun parseHeaders(headers: Headers): Map<String, String> {
        return headers
            // Keeping unsafe header makes webview throw [net::ERR_INVALID_ARGUMENT]
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid sending empty User-Agent, Chromium WebView will reset to default if empty
            setUserAgent(request.header("User-Agent") ?: defaultUserAgentProvider())
        }
    }
}

// Based on [IsRequestHeaderSafe] in
// https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}
private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie",
)
