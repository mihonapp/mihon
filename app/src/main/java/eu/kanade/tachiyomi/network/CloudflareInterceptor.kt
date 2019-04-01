package eu.kanade.tachiyomi.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.webkit.WebResourceResponse
import android.webkit.WebView
import eu.kanade.tachiyomi.util.WebViewClientCompat
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor(private val context: Context) : Interceptor {

    private val serverCheck = arrayOf("cloudflare-nginx", "cloudflare")

    private val handler by lazy {
        val thread = HandlerThread("WebViewThread").apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                Timber.e(e)
            }
            start()
        }
        Handler(thread.looper)
    }

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        // Check if Cloudflare anti-bot is on
        if (response.code() == 503 && response.header("Server") in serverCheck) {
            try {
                response.close()
                if (resolveWithWebView(chain.request())) {
                    // Retry original request
                    return chain.proceed(chain.request())
                } else {
                    throw Exception("Failed resolving Cloudflare challenge")
                }
            } catch (e: Exception) {
                // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
                // we don't crash the entire app
                throw IOException(e)
            }
        }

        return response
    }

    private fun isChallengeResolverUrl(url: String): Boolean {
        return "chk_jschl" in url
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(request: Request): Boolean {
        val latch = CountDownLatch(1)

        var result = false
        var isResolvingChallenge = false

        val requestUrl = request.url().toString()
        val headers = request.headers().toMultimap().mapValues { it.value.getOrNull(0) ?: "" }

        handler.post {
            val view = WebView(context)
            view.settings.javaScriptEnabled = true
            view.settings.userAgentString = request.header("User-Agent")
            view.webViewClient = object : WebViewClientCompat() {

                override fun shouldInterceptRequestCompat(
                        view: WebView,
                        url: String
                ): WebResourceResponse? {
                    val isChallengeResolverUrl = isChallengeResolverUrl(url)
                    if (requestUrl != url && !isChallengeResolverUrl) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }

                    if (isChallengeResolverUrl) {
                        isResolvingChallenge = true
                    }
                    return null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (isResolvingChallenge && url == requestUrl) {
                        setResultAndFinish(true)
                    }
                }

                override fun onReceivedErrorCompat(
                        view: WebView,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String,
                        isMainFrame: Boolean
                ) {
                    if ((errorCode != 503 && requestUrl == failingUrl) ||
                        isChallengeResolverUrl(failingUrl)
                    ) {
                        setResultAndFinish(false)
                    }
                }

                private fun setResultAndFinish(resolved: Boolean) {
                    result = resolved
                    latch.countDown()
                    view.stopLoading()
                    view.destroy()
                }
            }

            view.loadUrl(requestUrl, headers)
        }

        latch.await(12, TimeUnit.SECONDS)

        return result
    }

}
