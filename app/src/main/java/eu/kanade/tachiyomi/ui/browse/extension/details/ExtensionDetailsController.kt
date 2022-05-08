package eu.kanade.tachiyomi.ui.browse.extension.details

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.core.os.bundleOf
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.util.system.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl
import uy.kohesive.injekt.injectLazy

@SuppressLint("RestrictedApi")
class ExtensionDetailsController(bundle: Bundle? = null) :
    ComposeController<ExtensionDetailsPresenter>(bundle) {

    private val network: NetworkHelper by injectLazy()

    constructor(pkgName: String) : this(
        bundleOf(PKGNAME_KEY to pkgName),
    )

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle() = resources?.getString(R.string.label_extension_info)

    override fun createPresenter() = ExtensionDetailsPresenter(args.getString(PKGNAME_KEY)!!)

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        ExtensionDetailsScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickUninstall = { presenter.uninstallExtension() },
            onClickAppInfo = { presenter.openInSettings() },
            onClickSourcePreferences = { router.pushController(SourcePreferencesController(it)) },
            onClickSource = { presenter.toggleSource(it) },
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.extension_details, menu)

        presenter.extension?.let { extension ->
            menu.findItem(R.id.action_history).isVisible = !extension.isUnofficial
            menu.findItem(R.id.action_faq_and_guides).isVisible = !extension.isUnofficial
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_history -> openChangelog()
            R.id.action_faq_and_guides -> openReadme()
            R.id.action_enable_all -> toggleAllSources(true)
            R.id.action_disable_all -> toggleAllSources(false)
            R.id.action_clear_cookies -> clearCookies()
        }
        return super.onOptionsItemSelected(item)
    }

    fun onExtensionUninstalled() {
        router.popCurrentController()
    }

    private fun toggleAllSources(enable: Boolean) {
        presenter.toggleSources(enable)
    }

    private fun openChangelog() {
        val extension = presenter.extension!!
        val pkgName = extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension.")
        val pkgFactory = extension.pkgFactory
        if (extension.hasChangelog) {
            val url = createUrl(URL_EXTENSION_BLOB, pkgName, pkgFactory, "/CHANGELOG.md")
            openInBrowser(url)
            return
        }

        // Falling back on GitHub commit history because there is no explicit changelog in extension
        val url = createUrl(URL_EXTENSION_COMMITS, pkgName, pkgFactory)
        openInBrowser(url)
    }

    private fun openReadme() {
        val extension = presenter.extension!!

        if (!extension.hasReadme) {
            openInBrowser("https://tachiyomi.org/help/faq/#extensions")
            return
        }

        val pkgName = extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension.")
        val pkgFactory = extension.pkgFactory
        val url = createUrl(URL_EXTENSION_BLOB, pkgName, pkgFactory, "/README.md")
        openInBrowser(url)
        return
    }

    private fun createUrl(url: String, pkgName: String, pkgFactory: String?, path: String = ""): String {
        return when {
            !pkgFactory.isNullOrEmpty() -> "$url/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/$pkgFactory$path"
            else -> "$url/src/${pkgName.replace(".", "/")}$path"
        }
    }

    private fun clearCookies() {
        val urls = presenter.extension?.sources
            ?.filterIsInstance<HttpSource>()
            ?.map { it.baseUrl }
            ?.distinct() ?: emptyList()

        val cleared = urls.sumOf {
            network.cookieManager.remove(it.toHttpUrl())
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }
}

private const val PKGNAME_KEY = "pkg_name"
private const val URL_EXTENSION_COMMITS = "https://github.com/tachiyomiorg/tachiyomi-extensions/commits/master"
private const val URL_EXTENSION_BLOB = "https://github.com/tachiyomiorg/tachiyomi-extensions/blob/master"
