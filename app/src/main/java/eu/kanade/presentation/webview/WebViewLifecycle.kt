package eu.kanade.presentation.webview

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

internal class WebViewLifecycle {
    // Request interception reads the page URL off the UI thread.
    private val pageUrls = ConcurrentHashMap<WebView, String>()

    var isActive by mutableStateOf(true)
        private set

    fun register(view: WebView, url: String) {
        pageUrls[view] = url
    }

    fun updateUrl(view: WebView, url: String) {
        pageUrls.replace(view, url)
    }

    fun url(view: WebView): String? = pageUrls[view]

    fun release(view: WebView): String? {
        val url = pageUrls.remove(view) ?: return null
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        return url
    }

    fun close(onClose: () -> Unit = {}) {
        if (!isActive) return
        isActive = false
        onClose()
    }

    fun dispose() {
        close()
        pageUrls.keys.toList().forEach(::release)
    }
}
