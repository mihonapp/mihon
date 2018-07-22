package exh.ui.webview

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.clipboardManager
import eu.kanade.tachiyomi.util.toast
import exh.ui.login.LoginController
import kotlinx.android.synthetic.main.activity_webview.*
import uy.kohesive.injekt.injectLazy

class WebViewActivity : BaseActivity() {
    private val prefs: PreferencesHelper by injectLazy()
    private var mobileUserAgent: String? = null
    private var isDesktop: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(when (prefs.theme()) {
            2 -> R.style.Theme_Tachiyomi_Dark
            3 -> R.style.Theme_Tachiyomi_Amoled
            else -> R.style.Theme_Tachiyomi
        })

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        setSupportActionBar(toolbar)

        // Opaque status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val typedValue = TypedValue()
            theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true)
            window.statusBarColor = typedValue.data
        }

        // Show close button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)

        val url = intent.getStringExtra(KEY_URL)

        // Set immediately (required for correct title after rotation)
        title = url
        // Configure webview
        webview.settings.javaScriptEnabled = true
        webview.settings.domStorageEnabled = true
        webview.settings.databaseEnabled = true
        webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                invalidateOptionsMenu()
                appbar.setExpanded(true, true)
                title = "Loading: $url"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                title = url
                invalidateOptionsMenu()
            }
        }

        // Configure E-Hentai/ExHentai cookies
        for(domain in listOf(
                "www.exhentai.org",
                "exhentai.org",
                "www.e-hentai.org",
                "e-hentai.org",
                "g.e-hentai.org"
        )) {
            for(cookie in listOf(
                    LoginController.MEMBER_ID_COOKIE to prefs.memberIdVal().getOrDefault(),
                    LoginController.PASS_HASH_COOKIE to prefs.passHashVal().getOrDefault(),
                    LoginController.IGNEOUS_COOKIE to prefs.igneousVal().getOrDefault()
            )) {
                val cookieString = "${cookie.first}=${cookie.second}; domain=$domain; path=/;"
                CookieManager.getInstance().setCookie(domain, cookieString)
            }
        }

        // Long-click to copy URL
        toolbar.setOnLongClickListener {
            toast("URL copied.")
            clipboardManager.primaryClip = ClipData.newUri(
                    contentResolver,
                    webview.title,
                    Uri.parse(webview.url)
            )
            true
        }

        if(savedInstanceState == null) {
            mobileUserAgent = webview.settings.userAgentString

            if(url == null) {
                toast("No URL supplied!")
                finish()
                return
            }

            webview.loadUrl(url)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState?.getString(STATE_KEY_MOBILE_USER_AGENT)?.let {
            mobileUserAgent = it
        }
        savedInstanceState?.getBoolean(STATE_KEY_IS_DESKTOP)?.let {
            isDesktop = it
        }
        webview.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putString(STATE_KEY_MOBILE_USER_AGENT, mobileUserAgent)
        outState?.putBoolean(STATE_KEY_IS_DESKTOP, isDesktop)
        webview.saveState(outState)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_forward)?.isEnabled = webview.canGoForward()
        menu?.findItem(R.id.action_desktop_site)?.isChecked = isDesktop

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onBackPressed() {
        if(webview.canGoBack())
            webview.goBack()
        else
            super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            android.R.id.home -> finish()
            R.id.action_refresh -> webview.reload()
            R.id.action_forward -> webview.goForward()
            R.id.action_desktop_site -> {
                isDesktop = !item.isChecked
                item.isChecked = isDesktop

                (if(isDesktop) {
                    mobileUserAgent?.replace("\\([^(]*(Mobile|Android)[^)]*\\)"
                            .toRegex(RegexOption.IGNORE_CASE), "")
                            ?.replace("Mobile", "", true)
                            ?.replace("Android", "", true)
                } else {
                    mobileUserAgent
                })?.let {
                    webview.settings.userAgentString = it
                }

                webview.settings.useWideViewPort = isDesktop
                webview.settings.loadWithOverviewMode = isDesktop

                webview.reload()
            }
            R.id.action_open_in_browser ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webview.url)))
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val KEY_URL = "url"

        const val STATE_KEY_MOBILE_USER_AGENT = "mobile_user_agent"
        const val STATE_KEY_IS_DESKTOP = "is_desktop"
    }
}
