package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewClientCompat
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// TODO: Remove when OkHttp can handle http 103 responses
class Http103Interceptor(private val context: Context) : Interceptor {

    private val executor = ContextCompat.getMainExecutor(context)

    private val networkHelper: NetworkHelper by injectLazy()

    /**
     * When this is called, it initializes the WebView if it wasn't already. We use this to avoid
     * blocking the main thread too much. If used too often we could consider moving it to the
     * Application class.
     */
    private val initWebView by lazy {
        // Crashes on some devices. We skip this in some cases since the only impact is slower
        // WebView init in those rare cases.
        // See https://bugs.chromium.org/p/chromium/issues/detail?id=1279562
        if (DeviceUtil.isMiui || Build.VERSION.SDK_INT == Build.VERSION_CODES.S && DeviceUtil.isSamsung) {
            return@lazy
        }

        WebSettings.getDefaultUserAgent(context)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 103) return response
        if (!WebViewUtil.supportsWebView(context)) {
            launchUI {
                context.toast(R.string.information_webview_required, Toast.LENGTH_LONG)
            }
            return response
        }

        initWebView

        logcat { "Proceeding with WebView for request $request" }
        try {
            return proceedWithWebView(request, response)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    internal class JsInterface(private val latch: CountDownLatch, var payload: String? = null) {
        @JavascriptInterface
        fun passPayload(passedPayload: String) {
            payload = passedPayload
            latch.countDown()
        }
    }

    companion object {
        const val jsScript = "window.android.passPayload(document.querySelector('html').outerHTML)"

        val htmlMediaType = "text/html".toMediaType()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun proceedWithWebView(ogRequest: Request, ogResponse: Response): Response {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        val jsInterface = JsInterface(latch)

        var outerWebView: WebView? = null

        var exception: Exception? = null

        val requestUrl = ogRequest.url.toString()
        val headers = ogRequest.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }.toMutableMap()

        executor.execute {
            val webview = WebView(context).also { outerWebView = it }
            with(webview.settings) {
                javaScriptEnabled = true
                userAgentString = ogRequest.header("User-Agent") ?: networkHelper.defaultUserAgent
            }

            webview.addJavascriptInterface(jsInterface, "android")

            webview.webViewClient = object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(jsScript) {}
                }

                override fun onReceivedErrorCompat(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String,
                    isMainFrame: Boolean,
                ) {
                    if (isMainFrame) {
                        exception = Exception("Error $errorCode - $description")
                        latch.countDown()
                    }
                }
            }

            webview.loadUrl(requestUrl, headers)
        }

        latch.await(10, TimeUnit.SECONDS)

        executor.execute {
            outerWebView?.run {
                stopLoading()
                destroy()
            }
            outerWebView = null
        }

        exception?.let { throw it }

        val payload = jsInterface.payload ?: throw Exception("Couldn't fetch site through webview")

        return ogResponse.newBuilder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(payload.toResponseBody(htmlMediaType))
            .build()
    }
}
