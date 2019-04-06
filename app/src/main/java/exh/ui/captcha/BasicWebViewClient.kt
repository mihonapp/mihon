package exh.ui.captcha

import android.webkit.WebView
import android.webkit.WebViewClient

open class BasicWebViewClient(protected val activity: SolveCaptchaActivity,
                              protected val source: CaptchaCompletionVerifier,
                              private val injectScript: String?) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        if(source.verifyNoCaptcha(url)) {
            activity.finish()
        } else {
            if(injectScript != null) view.loadUrl("javascript:(function() {$injectScript})();")
        }
    }
}