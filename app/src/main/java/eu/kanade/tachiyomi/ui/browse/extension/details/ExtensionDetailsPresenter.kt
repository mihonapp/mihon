package eu.kanade.tachiyomi.ui.browse.extension.details

import android.app.Application
import android.os.Bundle
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.presentation.browse.ExtensionDetailsState
import eu.kanade.presentation.browse.ExtensionDetailsStateImpl
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionDetailsPresenter(
    private val pkgName: String,
    private val state: ExtensionDetailsStateImpl = ExtensionDetailsState() as ExtensionDetailsStateImpl,
    private val context: Application = Injekt.get(),
    private val getExtensionSources: GetExtensionSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
) : BasePresenter<ExtensionDetailsController>(), ExtensionDetailsState by state {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            extensionManager.getInstalledExtensionsFlow()
                .map { it.firstOrNull { it.pkgName == pkgName } }
                .collectLatest {
                    state.extension = it
                    fetchExtensionSources()
                }
        }

        bindToUninstalledExtension()
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

    private fun bindToUninstalledExtension() {
        presenterScope.launchIO {
            extensionManager.getInstalledExtensionsFlow()
                .drop(1)
                .filter { extensions -> extensions.none { it.pkgName == pkgName } }
                .map { }
                .take(1)
                .collectLatest {
                    launchUI {
                        view?.onExtensionUninstalled()
                    }
                }
        }
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
}

data class ExtensionSourceItem(
    val source: Source,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
