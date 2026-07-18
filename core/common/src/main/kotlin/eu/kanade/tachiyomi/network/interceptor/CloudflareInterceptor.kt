package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.ForegroundActivityHolder
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.jsoup.Jsoup
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        // Managed challenge: tagged by this header regardless of status code or body.
        if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) return true
        if (response.code !in ERROR_CODES) return false

        val server = response.header("Server")
        val fromCloudflare = server in SERVER_CHECK ||
            server?.contains("cloudflare", ignoreCase = true) == true ||
            response.header("cf-ray") != null
        if (!fromCloudflare) return false

        val body = response.peekBody(Long.MAX_VALUE).string()
        val document = Jsoup.parse(body, response.request.url.toString())

        // Classic interstitial (solve with webview only on captcha, not on geo block).
        if (document.getElementById("challenge-error-title") != null ||
            document.getElementById("challenge-error-text") != null
        ) {
            return true
        }
        // Managed / Turnstile challenge that the stock detection above misses.
        if (CHALLENGE_TITLES.any { document.title().contains(it, ignoreCase = true) }) return true
        return CHALLENGE_BODY_MARKERS.any { body.contains(it, ignoreCase = true) }
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url).firstOrNull { it.name == "cf_clearance" }

            // cf_clearance is bound to the browser's TLS fingerprint, so an OkHttp retry with only
            // the cookie is still rejected by managed challenges. Replay the request from inside the
            // solved WebView (same-origin fetch) so it carries the browser's TLS too.
            resolveWithWebView(request, oldCookie)?.let { return it }

            // Relay unavailable (cross-origin request, or bridge failure): plain retry with the now
            // fresh cf_clearance in the shared jar, enough for classic challenges.
            return chain.proceed(request)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    /**
     * Solves the Cloudflare challenge for [originalRequest] in a WebView and replays the request
     * from inside it, returning the [Response] (`null` if it couldn't be replayed, so the caller
     * falls back to a plain retry).
     *
     * @throws CloudflareBypassException if the challenge isn't solved in time.
     */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?): Response? {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)
        val relayBridge = RelayBridge()

        executor.execute {
            val view = createWebView(originalRequest)
            webview = view
            // Added before the first load so it survives the challenge's navigations to the solved page.
            view.addJavascriptInterface(relayBridge, RELAY_INTERFACE)
            // A managed / Turnstile challenge only runs its verification JS once rendered, so attach
            // the (otherwise headless) WebView to a real window or it silently fails to solve.
            attachToWindow(view)

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    // Managed challenges are served with HTTP 200, so the title is the only signal
                    // that we're still on the interstitial and must keep waiting.
                    val title = view.title.orEmpty()
                    if (CHALLENGE_TITLES.any { title.contains(it, ignoreCase = true) }) return

                    // Fresh clearance, or the real page loaded (challenge cleared / none present).
                    if (isCloudFlareBypassed() || (url == origRequestUrl && title.isNotBlank())) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }
                }
            }

            view.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        // Replay through the still-alive WebView so the fetch uses its TLS + cookies.
        val relayed = if (cloudflareBypassed) relayThroughWebView(webview, relayBridge, originalRequest) else null

        executor.execute {
            if (!cloudflareBypassed) isWebViewOutdated = webview?.isOutdated() == true
            webview?.run {
                stopLoading()
                removeJavascriptInterface(RELAY_INTERFACE)
                detachFromWindow(this)
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            if (isWebViewOutdated) context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            throw CloudflareBypassException()
        }

        return relayed
    }

    /**
     * Replays [request] with a same-origin `fetch()` run inside the solved [webView], bridged back
     * through [bridge]. Returns `null` if the fetch failed (e.g. cross-origin blocked by CORS).
     */
    private fun relayThroughWebView(webView: WebView?, bridge: RelayBridge, request: Request): Response? {
        webView ?: return null
        val relayLatch = CountDownLatch(1)
        bridge.reset(relayLatch)

        val bodyBase64 = request.body?.let {
            val buffer = Buffer().also(it::writeTo)
            Base64.getEncoder().encodeToString(buffer.readByteArray())
        }.orEmpty()
        val headersJson = JSONObject(parseHeaders(request.headers)).toString()

        val js = buildRelayScript(request.url.toString(), request.method, headersJson, bodyBase64)
        executor.execute { webView.evaluateJavascript(js, null) }

        if (!relayLatch.await(30, TimeUnit.SECONDS)) return null
        val payload = bridge.result ?: return null
        return runCatching { buildResponse(request, payload) }.getOrNull()
    }

    private fun buildResponse(request: Request, payload: String): Response {
        val json = JSONObject(payload)
        if (json.has("error")) throw IOException(json.optString("error"))

        val headersJson = json.optJSONObject("headers") ?: JSONObject()
        val headers = Headers.Builder().apply {
            // fetch() already decoded the body, so dropping these avoids OkHttp re-decoding it.
            headersJson.keys().asSequence()
                .filter { it.lowercase() !in RELAY_STRIPPED_HEADERS }
                .forEach { add(it, headersJson.getString(it)) }
        }.build()
        val contentType = headersJson.optString("content-type").ifBlank { null }?.toMediaTypeOrNull()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(json.getInt("status"))
            .message(json.optString("statusText").ifBlank { "OK" })
            .headers(headers)
            .body(Base64.getDecoder().decode(json.getString("body")).toResponseBody(contentType))
            .build()
    }

    /** Attaches [webView] 1x1 (invisible) to the foreground window so challenge JS renders; no-op if none. */
    private fun attachToWindow(webView: WebView) {
        val content = ForegroundActivityHolder.activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (webView.parent == null) content.addView(webView, ViewGroup.LayoutParams(1, 1))
    }

    private fun detachFromWindow(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    /** Bridges the in-page `fetch()` result back to the blocked OkHttp thread (called on the JS thread). */
    private class RelayBridge {
        @Volatile
        var result: String? = null
            private set

        @Volatile
        private var latch: CountDownLatch? = null

        fun reset(latch: CountDownLatch) {
            result = null
            this.latch = latch
        }

        @JavascriptInterface
        fun onResult(json: String) {
            result = json
            latch?.countDown()
        }

        @JavascriptInterface
        fun onError(error: String) {
            result = JSONObject().put("error", error).toString()
            latch?.countDown()
        }
    }
}

private fun buildRelayScript(url: String, method: String, headersJson: String, bodyBase64: String): String {
    // JSONObject.quote escapes the interpolated values into JS string literals.
    return """
        (function () {
            var opts = { method: ${JSONObject.quote(
        method,
    )}, headers: $headersJson, credentials: 'include', redirect: 'follow' };
            var bodyB64 = ${JSONObject.quote(bodyBase64)};
            if (bodyB64 && opts.method !== 'GET' && opts.method !== 'HEAD') {
                opts.body = Uint8Array.from(atob(bodyB64), function (c) { return c.charCodeAt(0); });
            }
            fetch(${JSONObject.quote(url)}, opts).then(function (r) {
                return r.arrayBuffer().then(function (ab) {
                    var buf = new Uint8Array(ab), bin = '', chunk = 0x8000;
                    for (var i = 0; i < buf.length; i += chunk) {
                        bin += String.fromCharCode.apply(null, buf.subarray(i, i + chunk));
                    }
                    var h = {};
                    r.headers.forEach(function (v, k) { h[k] = v; });
                    $RELAY_INTERFACE.onResult(JSON.stringify({ status: r.status, statusText: r.statusText, headers: h, body: btoa(bin) }));
                });
            }).catch(function (e) { $RELAY_INTERFACE.onError(String(e)); });
        })();
    """.trimIndent()
}

private const val RELAY_INTERFACE = "MihonCloudflareRelay"
private val RELAY_STRIPPED_HEADERS = setOf("content-encoding", "content-length", "transfer-encoding")
private val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")
private val CHALLENGE_BODY_MARKERS =
    listOf("challenges.cloudflare.com", "__cf_chl", "cf_chl_opt", "cf-browser-verification")
private val CHALLENGE_TITLES = listOf("just a moment", "attention required", "verifying", "verify you are human")

private class CloudflareBypassException : Exception()
