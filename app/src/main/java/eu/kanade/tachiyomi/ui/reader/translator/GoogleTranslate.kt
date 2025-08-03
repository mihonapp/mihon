package eu.kanade.tachiyomi.ui.reader.translator

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLEncoder
import kotlin.coroutines.resume

class GoogleTranslate(private val context: Context) {

    private var webView: WebView? = null
    private var handler: Handler? = null
    private var translationCallback: ((String) -> Unit)? = null

    init {
        handler = Handler(Looper.getMainLooper())
        handler?.post {
            webView = WebView(context)
            @SuppressLint("SetJavaScriptEnabled")
            webView?.settings?.javaScriptEnabled = true
            webView?.addJavascriptInterface(this, "Android")
            webView?.loadUrl("about:blank")
        }
    }

    @JavascriptInterface
    fun onTranslationResult(result: String) {
        translationCallback?.invoke(result)
    }

    suspend fun translate(text: List<String>, from: String, to: String): List<String> {
        return suspendCancellableCoroutine { continuation ->
            val joinedText = text.joinToString("\n")
            val encodedText = URLEncoder.encode(joinedText, "UTF-8")
            val url = "https://translate.google.com/?sl=$from&tl=$to&text=$encodedText"

            handler?.post {
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Sometimes the result is not immediately available
                        // We will try to get it a few times.
                        // This is a very fragile selector and might break if Google changes the website's layout.
                        val script = """
                            (function() {
                                var result = document.querySelector('span[jsname=\"W297wb\"]').innerText;
                                Android.onTranslationResult(result);
                            })();
                        """.trimIndent()

                        handler?.postDelayed({ view?.evaluateJavascript(script, null) }, 500)
                    }
                }

                translationCallback = { result ->
                    if (continuation.isActive) {
                        continuation.resume(result.split("\n"))
                    }
                }
                webView?.loadUrl(url)
            }

            continuation.invokeOnCancellation {
                handler?.post {
                    webView?.stopLoading()
                }
            }
        }
    }

    fun destroy() {
        handler?.post {
            webView?.destroy()
            webView = null
        }
    }
}
