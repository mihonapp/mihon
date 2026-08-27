package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.ForegroundActivity
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    private val iframeScript by lazy {
        javaClass
            .getResource("/assets/CloudflareSolverIframeScript.js")!!
            .readText()
            .replace("__SOLVER__", "__SOLVER_${(ULong.MIN_VALUE..ULong.MAX_VALUE).random()}__")
    }

    private val listenerScript = """
        addEventListener("message", ({data}) => {
            if (data?.source === "cloudflare-challenge") {
                mihon?.postMessage(data.event);
            }
        })
    """.trimIndent()

    override fun shouldIntercept(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        // Checking the cf-mitigated header is the official way to detect a Cloudflare challenge:
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun getNonce(url: HttpUrl): String? = cookieManager.get(url).firstOrNull {
        it.name == "cf_clearance"
    }?.value

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
        nonce: String?,
    ): Response? {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            resolveWithWebView(request, nonce)
            return null
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, originalNonce: String?) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        var iframeScriptHandler: ScriptHandler? = null
        var listenerScriptHandler: ScriptHandler? = null

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        executor.execute {
            webview = createWebView(originalRequest)

            with(webview) {
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }

            // Fallback solver that injects JavaScript to solve challenge
            fun injectIframeScript() {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    iframeScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                        webview,
                        iframeScript,
                        mutableSetOf("https://challenges.cloudflare.com"),
                    )
                    webview.loadUrl(origRequestUrl, headers)
                } else {
                    // Feature not supported, abort
                    latch.countDown()
                }
            }

            fun handleEvent(event: String) {
                when (event) {
                    "interactiveBegin" -> {
                        if (iframeScriptHandler != null) {
                            // Solving is done in injected iframe script, skip
                            return
                        }

                        // Get the current view group
                        val container = ForegroundActivity.current?.window?.decorView as? ViewGroup
                        if (container == null) {
                            injectIframeScript()
                            return
                        }

                        executor.execute {
                            val width = container.width.takeIf { it > 0 } ?: 1920
                            val height = container.height.takeIf { it > 0 } ?: 1080

                            // Set translationX to negative width.
                            // The WebView should be offscreen even when the orientation changes.
                            webview.translationX = -width.toFloat()

                            // Attach the WebView to the view group so we can send key events.
                            container.addView(webview, ViewGroup.LayoutParams(width, height))

                            // Send Tab and Space to check the checkbox, and abort if dispatchKeyEvent fails.
                            // Use a separate thread to unblock the main thread.
                            thread {
                                if (!webview.dispatchKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_TAB,
                                        ),
                                    )
                                ) {
                                    injectIframeScript()
                                    return@thread
                                }
                                Thread.sleep(100)
                                if (!webview.dispatchKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_UP,
                                            KeyEvent.KEYCODE_TAB,
                                        ),
                                    )
                                ) {
                                    injectIframeScript()
                                    return@thread
                                }
                                Thread.sleep(100)
                                if (!webview.dispatchKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_SPACE,
                                        ),
                                    )
                                ) {
                                    injectIframeScript()
                                    return@thread
                                }
                                Thread.sleep(100)
                                if (!webview.dispatchKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_UP,
                                            KeyEvent.KEYCODE_SPACE,
                                        ),
                                    )
                                ) {
                                    injectIframeScript()
                                    return@thread
                                }
                            }
                        }
                    }
                    "fail" -> {
                        // Challenge failed, abort
                        latch.countDown()
                    }
                }
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
                // Use an isolated world so the page cannot see our bridge
                val world = WebViewCompat.getExecutionWorld(webview, "mihon")
                val allowedOriginRules = mutableSetOf("${originalRequest.url.scheme}://${originalRequest.url.host}")

                WebViewCompat.addWebMessageListener(webview, "mihon", allowedOriginRules, world) {
                        _,
                        message,
                        _,
                        isMainFrame,
                        _,
                    ->
                    if (isMainFrame) {
                        message.data?.let { handleEvent(it) }
                    }
                }

                // Listen for message events
                listenerScriptHandler = WebViewCompat.addJavaScriptOnEvent(
                    webview,
                    listenerScript,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                    allowedOriginRules,
                    world,
                )
            } else {
                webview.addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun postMessage(event: String) = handleEvent(event)
                    },
                    "mihon",
                )
            }

            @SuppressLint("MissingOnRenderProcessGone")
            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (isBypassed(originalRequest.url, originalNonce)) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl) {
                        if (!challengeFound) {
                            // The first request didn't return the challenge, abort.
                            latch.countDown()
                        } else if (!WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
                            // Listen for message events
                            view.evaluateJavascript(
                                listenerScript,
                                null,
                            )
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.responseHeaders["cf-mitigated"] == "challenge") {
                            // Found the Cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    latch.countDown()
                    return true
                }
            }

            webview.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webview?.isOutdated() == true
            }

            webview?.let { webview ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
                    WebViewCompat.removeWebMessageListener(
                        webview,
                        WebViewCompat.getExecutionWorld(webview, "mihon"),
                        "mihon",
                    )
                } else {
                    webview.removeJavascriptInterface("mihon")
                }

                iframeScriptHandler?.remove()
                listenerScriptHandler?.remove()

                (webview.parent as? ViewGroup)?.removeView(webview)

                webview.run {
                    stopLoading()
                    destroy()
                }
            }
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // Prompt user to update WebView if it seems too outdated
            if (isWebViewOutdated) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }

            throw CloudflareBypassException()
        }
    }
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

private class CloudflareBypassException : Exception()
