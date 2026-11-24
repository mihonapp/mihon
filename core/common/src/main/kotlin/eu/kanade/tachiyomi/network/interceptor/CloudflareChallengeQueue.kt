package eu.kanade.tachiyomi.network.interceptor

import android.graphics.Rect
import android.webkit.WebView
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Cloudflare challenges waiting for the user, who is shown the first one.
 */
@Inject
@SingleIn(AppScope::class)
class CloudflareChallengeQueue {

    private val _challenges = MutableStateFlow(emptyList<CloudflareChallenge>())
    val challenges: StateFlow<List<CloudflareChallenge>> = _challenges.asStateFlow()

    /**
     * Returns false if the app isn't in the foreground to show it.
     */
    fun add(challenge: CloudflareChallenge): Boolean {
        if (_challenges.subscriptionCount.value == 0) return false
        _challenges.update { it + challenge }
        return true
    }

    fun remove(challenge: CloudflareChallenge) = _challenges.update { it - challenge }
}

class CloudflareChallenge(
    val host: String,
    val webView: WebView,
    val cancel: () -> Unit,
) {
    /** Where the captcha is in [webView], null until known. */
    val widget = MutableStateFlow<Rect?>(null)
}
