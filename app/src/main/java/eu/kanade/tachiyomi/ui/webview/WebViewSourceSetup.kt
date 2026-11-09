package eu.kanade.tachiyomi.ui.webview

import android.net.http.SslCertificate
import android.os.Build
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LocalProxy
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/** What the app's WebView takes from the source it was opened for: its headers, and its proxy if it runs one. */
class WebViewSourceSetup(val headers: Map<String, String>, val proxy: LocalProxy?)

/** The setup for a WebView opened for [source]; whatever the source fails to give, the WebView goes without. */
suspend fun webViewSourceSetup(source: HttpSource?): WebViewSourceSetup = withContext(Dispatchers.IO) {
    val headers = try {
        source?.headers?.toWebViewHeaders().orEmpty()
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to build headers" }
        emptyMap()
    }
    // Not cancelled halfway, so a proxy the source opened is either handed over or closed here
    val proxy = withContext(NonCancellable) {
        val proxy = try {
            source?.openWebViewProxy()
        } catch (e: Throwable) {
            // Extensions built against another API can fail with linkage errors too
            logcat(LogPriority.ERROR, e) { "Failed to open the source's WebView proxy" }
            null
        }
        proxy?.takeIf { WebViewUtil.setProxy(it.address) } ?: run {
            proxy?.closeQuietly()
            null
        }
    }
    if (!isActive) {
        proxy?.closeQuietly()
        throw CancellationException("WebView closed while the source's proxy opened")
    }
    WebViewSourceSetup(headers, proxy)
}

/** Points the app's WebViews away from the source's proxy and closes it, logging what goes wrong. */
fun LocalProxy.closeQuietly() {
    WebViewUtil.clearProxy()
    try {
        close()
    } catch (e: Throwable) {
        logcat(LogPriority.ERROR, e) { "Failed to close the source's WebView proxy" }
    }
}

/** The DER encoding of [certificate], from an SSL error, or null without one. */
fun derEncoding(certificate: SslCertificate): ByteArray? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        certificate.x509Certificate?.encoded
    } else {
        SslCertificate.saveState(certificate)?.getByteArray("x509-certificate")
    }
} catch (_: Exception) {
    null
}
