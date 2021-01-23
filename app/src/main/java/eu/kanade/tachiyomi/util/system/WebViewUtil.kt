package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import eu.kanade.tachiyomi.util.lang.launchUI
import timber.log.Timber

object WebViewUtil {
    val WEBVIEW_UA_VERSION_REGEX by lazy {
        Regex(""".*Chrome/(\d+)\..*""")
    }

    var DEFAULT_USER_AGENT: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        private set

    const val REQUESTED_WITH = "com.android.browser"

    const val MINIMUM_WEBVIEW_VERSION = 87

    fun supportsWebView(context: Context): Boolean {
        try {
            // May throw android.webkit.WebViewFactory$MissingWebViewPackageException if WebView
            // is not installed
            CookieManager.getInstance()

            launchUI {
                DEFAULT_USER_AGENT = WebView(context)
                    .getDefaultUserAgentString()
                    .replace("; wv", "")
            }
        } catch (e: Exception) {
            Timber.e(e)
            return false
        }

        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        setAppCacheEnabled(true)
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
    }
}

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = WebViewUtil.WEBVIEW_UA_VERSION_REGEX.matchEntire(getDefaultUserAgentString())
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
