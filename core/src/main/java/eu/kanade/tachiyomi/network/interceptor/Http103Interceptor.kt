package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.util.system.WebViewClientCompat
import eu.kanade.tachiyomi.util.system.logcat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CountDownLatch

// TODO: Remove when OkHttp can handle HTTP 103 responses
class Http103Interceptor(context: Context) : WebViewInterceptor(context) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        return response.code == 103
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        logcat { "Proceeding with WebView for request $request" }
        try {
            return proceedWithWebView(request, response)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun proceedWithWebView(originalRequest: Request, originalResponse: Response): Response {
        // We need to lock this thread until the WebView loads the page, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        val jsInterface = JsInterface(latch)

        var webview: WebView? = null

        var exception: Exception? = null

        val requestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        executor.execute {
            webview = createWebView(originalRequest)
            webview?.addJavascriptInterface(jsInterface, "android")

            webview?.webViewClient = object : WebViewClientCompat() {
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

            webview?.loadUrl(requestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        exception?.let { throw it }

        val responseHtml = jsInterface.responseHtml ?: throw Exception("Couldn't fetch site through webview")

        return originalResponse.newBuilder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .body(responseHtml.toResponseBody(htmlMediaType))
            .build()
    }
}

internal class JsInterface(private val latch: CountDownLatch, var responseHtml: String? = null) {
    @Suppress("UNUSED")
    @JavascriptInterface
    fun passPayload(passedPayload: String) {
        responseHtml = passedPayload
        latch.countDown()
    }
}

private const val jsScript = "window.android.passPayload(document.querySelector('html').outerHTML)"
private val htmlMediaType = "text/html".toMediaType()
