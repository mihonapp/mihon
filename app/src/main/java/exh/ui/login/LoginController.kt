package exh.ui.login

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.EhActivityLoginBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import exh.uconfig.WarnConfigureDialogController
import java.net.HttpCookie
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * LoginController
 */

class LoginController : NucleusController<EhActivityLoginBinding, LoginPresenter>() {
    val preferenceManager: PreferencesHelper by injectLazy()

    val sourceManager: SourceManager by injectLazy()

    override fun getTitle() = "ExHentai login"

    override fun createPresenter() = LoginPresenter()

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = EhActivityLoginBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        with(view) {
            binding.btnCancel.setOnClickListener { router.popCurrentController() }

            binding.btnAdvanced.setOnClickListener {
                binding.advancedOptions.visible()
                binding.webview.gone()
                binding.btnAdvanced.isEnabled = false
                binding.btnCancel.isEnabled = false
            }

            binding.btnClose.setOnClickListener {
                hideAdvancedOptions(this)
            }

            binding.btnRecheck.setOnClickListener {
                hideAdvancedOptions(this)
                binding.webview.loadUrl("https://exhentai.org/")
            }

            binding.btnAltLogin.setOnClickListener {
                hideAdvancedOptions(this)
                binding.webview.loadUrl("https://e-hentai.org/bounce_login.php")
            }

            binding.btnSkipRestyle.setOnClickListener {
                hideAdvancedOptions(this)
                binding.webview.loadUrl("https://forums.e-hentai.org/index.php?act=Login&$PARAM_SKIP_INJECT=true")
            }

            CookieManager.getInstance().removeAllCookies {
                launchUI {
                    startWebview(view)
                }
            }
        }
    }

    private fun hideAdvancedOptions(view: View) {
        binding.advancedOptions.gone()
        binding.webview.visible()
        binding.btnAdvanced.isEnabled = true
        binding.btnCancel.isEnabled = true
    }

    fun startWebview(view: View) {
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.domStorageEnabled = true

        binding.webview.loadUrl("https://forums.e-hentai.org/index.php?act=Login")

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Timber.d(url)
                val parsedUrl = Uri.parse(url)
                if (parsedUrl.host.equals("forums.e-hentai.org", ignoreCase = true)) {
                    // Hide distracting content
                    if (!parsedUrl.queryParameterNames.contains(PARAM_SKIP_INJECT)) {
                        view.evaluateJavascript(HIDE_JS, null)
                    }
                    // Check login result

                    if (parsedUrl.getQueryParameter("code")?.toInt() != 0) {
                        if (checkLoginCookies(url)) view.loadUrl("https://exhentai.org/")
                    }
                } else if (parsedUrl.host.equals("exhentai.org", ignoreCase = true)) {
                    // At ExHentai, check that everything worked out...
                    if (applyExHentaiCookies(url)) {
                        preferenceManager.enableExhentai().set(true)
                        finishLogin()
                    }
                }
            }
        }
    }

    fun finishLogin() {
        router.popCurrentController()

        // Upload settings
        WarnConfigureDialogController.uploadSettings(router)
    }

    /**
     * Check if we are logged in
     */
    fun checkLoginCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->
            return parsed.filter {
                (
                    it.name.equals(MEMBER_ID_COOKIE, ignoreCase = true) ||
                        it.name.equals(PASS_HASH_COOKIE, ignoreCase = true)
                    ) &&
                    it.value.isNotBlank()
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

            // Missing a cookie
            if (memberId == null || passHash == null || igneous == null) return false

            // Update prefs
            preferenceManager.memberIdVal().set(memberId)
            preferenceManager.passHashVal().set(passHash)
            preferenceManager.igneousVal().set(igneous)

            return true
        }
        return false
    }

    fun getCookies(url: String): List<HttpCookie>? =
        CookieManager.getInstance().getCookie(url)?.let {
            it.split("; ").flatMap {
                HttpCookie.parse(it)
            }
        }

    companion object {
        const val PARAM_SKIP_INJECT = "TEH_SKIP_INJECT"

        const val MEMBER_ID_COOKIE = "ipb_member_id"
        const val PASS_HASH_COOKIE = "ipb_pass_hash"
        const val IGNEOUS_COOKIE = "igneous"

        const val HIDE_JS =
            """
                    javascript:(function () {
                        document.getElementsByTagName('body')[0].style.visibility = 'hidden';
                        document.getElementsByName('submit')[0].style.visibility = 'visible';
                        document.querySelector('td[width="60%"][valign="top"]').style.visibility = 'visible';

                        function hide(e) {if(e != null) e.style.display = 'none';}

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
                        if(error != null) error.style.visibility = 'visible';
                        hide(fh[0]);
                        hide(fh[1]);
                        hide(document.querySelector("#gfooter"));
                        hide(document.querySelector(".copyright"));
                        document.querySelectorAll("td").forEach(function(e) {
                            e.style.color = "white";
                        });
                        var pc = document.querySelector(".postcolor");
                        if(pc != null) pc.style.color = "#26353F";
                    })()
                    """
    }
}
