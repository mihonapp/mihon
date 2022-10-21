package eu.kanade.tachiyomi.ui.browse.extension.details

import android.app.Application
import android.os.Bundle
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.presentation.browse.ExtensionDetailsState
import eu.kanade.presentation.browse.ExtensionDetailsStateImpl
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionDetailsPresenter(
    private val pkgName: String,
    private val state: ExtensionDetailsStateImpl = ExtensionDetailsState() as ExtensionDetailsStateImpl,
    private val context: Application = Injekt.get(),
    private val getExtensionSources: GetExtensionSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
) : BasePresenter<ExtensionDetailsController>(), ExtensionDetailsState by state {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            extensionManager.installedExtensionsFlow
                .map { it.firstOrNull { pkg -> pkg.pkgName == pkgName } }
                .collectLatest { extension ->
                    // If extension is null it's most likely uninstalled
                    if (extension == null) {
                        withUIContext {
                            view?.onExtensionUninstalled()
                        }
                        return@collectLatest
                    }
                    state.extension = extension
                    fetchExtensionSources()
                }
        }
    }

    private fun CoroutineScope.fetchExtensionSources() {
        launchIO {
            getExtensionSources.subscribe(extension!!)
                .map {
                    it.sortedWith(
                        compareBy(
                            { item -> item.enabled.not() },
                            { item -> if (item.labelAsName) item.source.name else LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase() },
                        ),
                    )
                }
                .collectLatest {
                    state.isLoading = false
                    state.sources = it
                }
        }
    }

    fun getChangelogUrl(): String {
        extension ?: return ""

        val pkgName = extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension.")
        val pkgFactory = extension.pkgFactory
        if (extension.hasChangelog) {
            return createUrl(URL_EXTENSION_BLOB, pkgName, pkgFactory, "/CHANGELOG.md")
        }

        // Falling back on GitHub commit history because there is no explicit changelog in extension
        return createUrl(URL_EXTENSION_COMMITS, pkgName, pkgFactory)
    }

    fun getReadmeUrl(): String {
        extension ?: return ""

        if (!extension.hasReadme) {
            return "https://tachiyomi.org/help/faq/#extensions"
        }

        val pkgName = extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension.")
        val pkgFactory = extension.pkgFactory
        return createUrl(URL_EXTENSION_BLOB, pkgName, pkgFactory, "/README.md")
    }

    fun clearCookies() {
        val urls = extension?.sources
            ?.filterIsInstance<HttpSource>()
            ?.map { it.baseUrl }
            ?.distinct() ?: emptyList()

        val cleared = urls.sumOf {
            network.cookieManager.remove(it.toHttpUrl())
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }

    fun uninstallExtension() {
        val extension = extension ?: return
        extensionManager.uninstallExtension(extension.pkgName)
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        extension?.sources?.forEach { toggleSource.await(it.id, enable) }
    }

    private fun createUrl(url: String, pkgName: String, pkgFactory: String?, path: String = ""): String {
        return if (!pkgFactory.isNullOrEmpty()) {
            when (path.isEmpty()) {
                true -> "$url/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/$pkgFactory"
                else -> "$url/multisrc/overrides/$pkgFactory/" + (pkgName.split(".").lastOrNull() ?: "") + path
            }
        } else {
            url + "/src/" + pkgName.replace(".", "/") + path
        }
    }
}

data class ExtensionSourceItem(
    val source: Source,
    val enabled: Boolean,
    val labelAsName: Boolean,
)

private const val URL_EXTENSION_COMMITS = "https://github.com/tachiyomiorg/tachiyomi-extensions/commits/master"
private const val URL_EXTENSION_BLOB = "https://github.com/tachiyomiorg/tachiyomi-extensions/blob/master"
