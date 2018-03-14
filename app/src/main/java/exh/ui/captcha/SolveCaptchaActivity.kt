package exh.ui.captcha

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.android.synthetic.main.eh_activity_captcha.*
import uy.kohesive.injekt.injectLazy
import java.net.URL

class SolveCaptchaActivity : AppCompatActivity() {
    private val sourceManager: SourceManager by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.eh_activity_captcha)

        val sourceId = intent.getLongExtra(SOURCE_ID_EXTRA, -1)
        val source = if(sourceId != -1L)
            sourceManager.get(sourceId) as? CaptchaCompletionVerifier
        else null

        val cookies: HashMap<String, String>?
                = intent.getSerializableExtra(COOKIES_EXTRA) as? HashMap<String, String>

        val script: String? = intent.getStringExtra(SCRIPT_EXTRA)

        val url: String? = intent.getStringExtra(URL_EXTRA)

        if(source == null || cookies == null || url == null) {
            finish()
            return
        }

        toolbar.title = source.name + ": Solve captcha"

        val parsedUrl = URL(url)

        val cm = CookieManager.getInstance()

        fun continueLoading() {
            cookies.forEach { (t, u) ->
                val cookieString = t + "=" + u + "; domain=" + parsedUrl.host
                cm.setCookie(url, cookieString)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                CookieSyncManager.createInstance(this).sync()

            webview.settings.javaScriptEnabled = true
            webview.settings.domStorageEnabled = true

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    if(source.verify(url)) {
                        finish()
                    } else {
                        view.loadUrl("javascript:(function() {$script})();")
                    }
                }
            }

            webview.loadUrl(url)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.removeAllCookies { continueLoading() }
        } else {
            cm.removeAllCookie()
            continueLoading()
        }

        setSupportActionBar(toolbar)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val SOURCE_ID_EXTRA = "source_id_extra"
        const val COOKIES_EXTRA = "cookies_extra"
        const val SCRIPT_EXTRA = "script_extra"
        const val URL_EXTRA = "url_extra"

        fun launch(context: Context,
                   source: CaptchaCompletionVerifier,
                   cookies: Map<String, String>,
                   script: String,
                   url: String) {
            val intent = Intent(context, SolveCaptchaActivity::class.java).apply {
                putExtra(SOURCE_ID_EXTRA, source.id)
                putExtra(COOKIES_EXTRA, HashMap(cookies))
                putExtra(SCRIPT_EXTRA, script)
                putExtra(URL_EXTRA, url)
            }

            context.startActivity(intent)
        }
    }
}

interface CaptchaCompletionVerifier : Source {
    fun verify(url: String): Boolean
}

