package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.resume

object WebViewUtil {
    private const val CHROME_PACKAGE = "com.android.chrome"
    private const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"

    const val MINIMUM_WEBVIEW_VERSION = 118

    /**
     * Uses the WebView's user agent string to create something similar to what Chrome on Android
     * would return.
     *
     * Example of WebView user agent string:
     *   Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36
     *
     * Example of Chrome on Android:
     *   Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.3
     */
    fun getInferredUserAgent(context: Context): String {
        return WebView(context)
            .getDefaultUserAgentString()
            .replace("; Android .*?\\)".toRegex(), "; Android 10; K)")
            .replace("Version/.* Chrome/".toRegex(), "Chrome/")
    }

    fun getVersion(context: Context): String {
        val webView = WebView.getCurrentWebViewPackage() ?: return "how did you get here?"
        val pm = context.packageManager
        val label = webView.applicationInfo!!.loadLabel(pm)
        val version = webView.versionName
        return "$label $version"
    }

    fun supportsWebView(context: Context): Boolean {
        try {
            // May throw android.webkit.WebViewFactory$MissingWebViewPackageException if WebView
            // is not installed
            CookieManager.getInstance()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            return false
        }

        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    fun spoofedPackageName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(CHROME_PACKAGE, PackageManager.GET_META_DATA)

            CHROME_PACKAGE
        } catch (_: PackageManager.NameNotFoundException) {
            SYSTEM_SETTINGS_PACKAGE
        }
    }

    fun getMessageFromHttpStatusCode(code: Int): String {
        return when (code) {
            100 -> "Continue"
            101 -> "Switching Protocols"
            102 -> "Processing"
            103 -> "Early Hints"
            200 -> "OK"
            201 -> "Created"
            202 -> "Accepted"
            203 -> "Non-Authoritative Information"
            204 -> "No Content"
            205 -> "Reset Content"
            206 -> "Partial Content"
            207 -> "Multi-Status"
            208 -> "Already Reported"
            226 -> "IM Used"
            300 -> "Multiple Choices"
            301 -> "Moved Permanently"
            302 -> "Found"
            303 -> "See Other"
            304 -> "Not Modified"
            305 -> "Use Proxy"
            306 -> "unused"
            307 -> "Temporary Redirect"
            308 -> "Permanent Redirect"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            402 -> "Payment Required"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            406 -> "Not Acceptable"
            407 -> "Proxy Authentication Required"
            408 -> "Request Timeout"
            409 -> "Conflict"
            410 -> "Gone"
            411 -> "Length Required"
            412 -> "Precondition Failed"
            413 -> "Content Too Large"
            414 -> "URI Too Long"
            415 -> "Unsupported Media Type"
            416 -> "Range Not Satisfiable"
            417 -> "Expectation Failed"
            418 -> "I'm a teapot"
            421 -> "Misdirected Request"
            422 -> "Unprocessable Content"
            423 -> "Locked"
            424 -> "Failed Dependency"
            425 -> "Too Early"
            426 -> "Upgrade Required"
            428 -> "Precondition Required"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            451 -> "Unavailable For Legal Reasons"
            500 -> "Internal Server Error"
            501 -> "Not Implemented"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            505 -> "HTTP Version Not Supported"
            506 -> "Variant Also Negotiates"
            507 -> "Insufficient Storage"
            508 -> "Loop Detected"
            510 -> "Not Extended"
            511 -> "Network Authentication Required"
            else -> "Unknown"
        }
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

suspend fun WebView.getHtml(): String = suspendCancellableCoroutine {
    evaluateJavascript("document.documentElement.outerHTML") { html -> it.resume(html) }
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Allow zooming
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

// Based on https://stackoverflow.com/a/29218966
private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString

    // Revert to original UA string
    settings.userAgentString = originalUA

    return defaultUserAgentString
}
