package android.webkit

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mihon.extension.host.ExtensionExecutionContext
import mihon.extension.host.WebViewBridge
import mihon.extension.ipc.WebViewRequest
import java.util.UUID

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}
annotation class JavascriptInterface

open class WebSettings {
    var javaScriptEnabled = true
    var domStorageEnabled = true
    var databaseEnabled = false
    var useWideViewPort = true
    var loadWithOverviewMode = true
    var loadsImagesAutomatically = true
    var blockNetworkImage = false
    var cacheMode = LOAD_DEFAULT
    var userAgentString = ""
    var mediaPlaybackRequiresUserGesture = true
    var mixedContentMode = MIXED_CONTENT_NEVER_ALLOW
    companion object {
        const val LOAD_DEFAULT = -1
        const val LOAD_NO_CACHE = 2
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0

        @JvmStatic fun getDefaultUserAgent(context: Context): String = kotlinx.coroutines.runBlocking {
            WebViewBridge.execute(WebViewBridge.request("userAgent")).value
        }
    }
}
interface WebResourceRequest {
    val url: android.net.Uri
    val isForMainFrame: Boolean
    val isRedirect: Boolean
    fun hasGesture(): Boolean
    val method: String
    val requestHeaders: Map<String, String>
}
abstract class WebResourceError {
    abstract val errorCode: Int
    abstract val description: CharSequence
}
open class WebViewClient {
    open fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { }
    open fun onPageFinished(view: WebView?, url: String?) { }
    open fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) { }
    open fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
    open fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
    open fun onLoadResource(view: WebView?, url: String?) { }
    open fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) { }
    open fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? = null
    open fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? = null
}
open class WebChromeClient {
    open fun onProgressChanged(view: WebView?, newProgress: Int) { }
}
class WebResourceResponse(val mimeType: String?, val encoding: String?, val data: java.io.InputStream?)

open class WebView(context: Context) : android.view.ViewGroup(context) {
    val settings = WebSettings()
    var webViewClient = WebViewClient()
    var webChromeClient: WebChromeClient? = null
    var url: String? = null
        private set
    val originalUrl: String? get() = url
    private val identity = WebViewBridge.identity()
    private val session = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var load: Job? = null
    private var destroyed = false
    private fun request(
        action: String,
        target: String = url.orEmpty(),
        headers: Map<String, String> = emptyMap(),
        script: String = "",
    ) = WebViewRequest(
        action,
        requireNotNull(identity.packageId),
        requireNotNull(identity.sourceId),
        session,
        target,
        headers,
        script,
    )
    fun loadUrl(target: String) = loadUrl(target, emptyMap())
    fun loadUrl(target: String, additionalHttpHeaders: Map<String, String>) {
        check(!destroyed) { "WebView was destroyed" }
        if (target.startsWith("javascript:")) {
            evaluateJavascript(target.removePrefix("javascript:"), null)
            return
        }
        url = target
        load?.cancel()
        load = scope.launch {
            ExtensionExecutionContext.withIdentity(identity) {
                try {
                    post { webViewClient.onPageStarted(this@WebView, target, null) }
                    val headers = if (settings.userAgentString.isNotBlank()) {
                        mapOf("User-Agent" to settings.userAgentString) +
                            additionalHttpHeaders
                    } else {
                        additionalHttpHeaders
                    }
                    WebViewBridge.execute(request("open", target, headers))
                    post {
                        webChromeClient?.onProgressChanged(this@WebView, 100)
                        webViewClient.onPageFinished(this@WebView, target)
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (
                    failure: Exception,
                ) {
                    post { webViewClient.onReceivedError(this@WebView, -1, failure.message, target) }
                }
            }
        }
    }
    fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
        check(!destroyed)
        scope.launch {
            ExtensionExecutionContext.withIdentity(identity) {
                val value = WebViewBridge.execute(request("evaluate", script = script)).value
                post { callback?.onReceiveValue(value) }
            }
        }
    }
    fun stopLoading() {
        load?.cancel()
        runBlocking { WebViewBridge.execute(request("destroy")) }
    }
    fun destroy() {
        if (destroyed) return
        destroyed = true
        scope.cancel()
        runBlocking { WebViewBridge.execute(request("destroy")) }
    }
    fun clearCache(includeDiskFiles: Boolean) { }
    fun clearHistory() { }
    fun onPause() { }
    fun onResume() { }
    companion object {
        @JvmStatic fun setWebContentsDebuggingEnabled(enabled: Boolean) { }
    }
}

class CookieManager private constructor() {
    fun getCookie(url: String): String? = runBlocking {
        WebViewBridge.execute(WebViewBridge.request("getCookie", url = url)).value.takeIf(String::isNotBlank)
    }
    fun setCookie(
        url: String,
        value: String,
    ) {
        runBlocking { WebViewBridge.execute(WebViewBridge.request("setCookie", url = url, value = value)) }
    }
    fun setCookie(
        url: String,
        value: String,
        callback: ValueCallback<Boolean>?,
    ) {
        setCookie(url, value)
        callback?.onReceiveValue(true)
    }
    fun flush() {
        runBlocking { WebViewBridge.execute(WebViewBridge.request("cookies")) }
    }
    fun setAcceptCookie(accept: Boolean) { }
    fun setAcceptThirdPartyCookies(view: WebView, accept: Boolean) { }
    companion object {
        private val instance = CookieManager()

        @JvmStatic fun getInstance(): CookieManager = instance
    }
}
