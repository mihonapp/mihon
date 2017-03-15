package exh.ui.login

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import exh.EXH_SOURCE_ID
import kotlinx.android.synthetic.main.eh_activity_login.*
import kotlinx.android.synthetic.main.toolbar.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.net.HttpCookie

/**
 * LoginActivity
 */

class LoginActivity : BaseActivity() {

    val preferenceManager: PreferencesHelper by injectLazy()

    val sourceManager: SourceManager by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        setAppTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.eh_activity_login)

        setup()

        setupToolbar(toolbar, backNavigation = false)
    }

    fun setup() {
        btn_cancel.setOnClickListener { onBackPressed() }
        btn_recheck.setOnClickListener { webview.loadUrl("http://exhentai.org/") }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies {
                runOnUiThread {
                    startWebview()
                }
            }
        } else {
            CookieManager.getInstance().removeAllCookie()
            startWebview()
        }
    }

    fun startWebview() {
        webview.settings.javaScriptEnabled = true
        webview.settings.domStorageEnabled = true

        webview.loadUrl("https://forums.e-hentai.org/index.php?act=Login")

        webview.setWebViewClient(object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Timber.d(url)
                val parsedUrl = Uri.parse(url)
                if(parsedUrl.host.equals("forums.e-hentai.org", ignoreCase = true)) {
                    //Hide distracting content
                    view.loadUrl(HIDE_JS)

                    //Check login result
                    if(parsedUrl.getQueryParameter("code")?.toInt() != 0) {
                        if(checkLoginCookies(url)) view.loadUrl("http://exhentai.org/")
                    }
                } else if(parsedUrl.host.equals("exhentai.org", ignoreCase = true)) {
                    //At ExHentai, check that everything worked out...
                    if(applyExHentaiCookies(url)) {
                        preferenceManager.enableExhentai().set(true)
                        finishLogin()
                    }
                }
            }
        })
    }

    fun finishLogin() {
        val progressDialog = MaterialDialog.Builder(this)
                .title("Finalizing login")
                .progress(true, 0)
                .content("Please wait...")
                .cancelable(false)
                .show()

        val eh = sourceManager
                .getOnlineSources()
                .find { it.id == EXH_SOURCE_ID } as EHentai
        Observable.fromCallable {
            //I honestly have no idea why we need to call this twice, but it works, so whatever
            try {
                eh.fetchFavorites()
            } catch(ignored: Exception) {}
            try {
                eh.fetchFavorites()
            } catch(ignored: Exception) {}
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    progressDialog.dismiss()
                    onBackPressed()
                }
    }

    /**
     * Check if we are logged in
     */
    fun checkLoginCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->
            return parsed.filter {
                (it.name.equals(MEMBER_ID_COOKIE, ignoreCase = true)
                        || it.name.equals(PASS_HASH_COOKIE, ignoreCase = true))
                        && it.value.isNotBlank()
            }.count() >= 2
        }
        return false
    }

    /**
     * Parse cookies at ExHentai
     */
    fun applyExHentaiCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->

            var memberId: String? = null
            var passHash: String? = null
            var igneous: String? = null

            parsed.forEach {
                when (it.name.toLowerCase()) {
                    MEMBER_ID_COOKIE -> memberId = it.value
                    PASS_HASH_COOKIE -> passHash = it.value
                    IGNEOUS_COOKIE -> igneous = it.value
                }
            }

            //Missing a cookie
            if (memberId == null || passHash == null || igneous == null) return false

            //Update prefs
            preferenceManager.memberIdVal().set(memberId)
            preferenceManager.passHashVal().set(passHash)
            preferenceManager.igneousVal().set(igneous)

            return true
        }
        return false
    }

    fun getCookies(url: String): List<HttpCookie>?
            = CookieManager.getInstance().getCookie(url)?.let {
        it.split("; ").flatMap {
            HttpCookie.parse(it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        const val MEMBER_ID_COOKIE = "ipb_member_id"
        const val PASS_HASH_COOKIE = "ipb_pass_hash"
        const val IGNEOUS_COOKIE = "igneous"

        const val HIDE_JS = """
                    javascript:(function () {
                        document.getElementsByTagName('body')[0].style.visibility = 'hidden';
                        document.getElementsByName('submit')[0].style.visibility = 'visible';
                        document.querySelector('td[width="60%"][valign="top"]').style.visibility = 'visible';

                        function hide(e) {if(e !== null && e !== undefined) e.style.display = 'none';}

                        hide(document.querySelector(".errorwrap"));
                        hide(document.querySelector('td[width="40%"][valign="top"]'));
                        var child = document.querySelector(".page").querySelector('div');
                        child.style.padding = null;
                        var ft = child.querySelectorAll('table');
                        var fd = child.parentNode.querySelectorAll('div > div');
                        var fh = document.querySelector('#border').querySelectorAll('td > table');
                        hide(ft[0]);
                        hide(ft[1]);
                        hide(fd[1]);
                        hide(fd[2]);
                        hide(child.querySelector('br'));
                        var error = document.querySelector(".page > div > .borderwrap");
                        if(error !== null) error.style.visibility = 'visible';
                        hide(fh[0]);
                        hide(fh[1]);
                        hide(document.querySelector("#gfooter"));
                        hide(document.querySelector(".copyright"));
                        document.querySelectorAll("td").forEach(function(e) {
                            e.style.color = "white";
                        });
                        var pc = document.querySelector(".postcolor");
                        if(pc !== null) pc.style.color = "#26353F";
                    })()
                    """
    }
}
