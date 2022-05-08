package eu.kanade.tachiyomi.ui.browse.extension.details

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionDetailsPresenter(
    private val pkgName: String,
    private val context: Application = Injekt.get(),
    private val getExtensionSources: GetExtensionSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
) : BasePresenter<ExtensionDetailsController>() {

    val extension = extensionManager.installedExtensions.find { it.pkgName == pkgName }

    private val _state: MutableStateFlow<List<ExtensionSourceItem>> = MutableStateFlow(emptyList())
    val sourcesState: StateFlow<List<ExtensionSourceItem>> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        val extension = extension ?: return

        bindToUninstalledExtension()

        presenterScope.launchIO {
            getExtensionSources.subscribe(extension)
                .map {
                    it.sortedWith(
                        compareBy(
                            { item -> item.enabled.not() },
                            { item -> if (item.labelAsName) item.source.name else LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase() },
                        ),
                    )
                }
                .collectLatest { _state.value = it }
        }
    }

    private fun bindToUninstalledExtension() {
        extensionManager.getInstalledExtensionsObservable()
            .skip(1)
            .filter { extensions -> extensions.none { it.pkgName == pkgName } }
            .map { }
            .take(1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                view.onExtensionUninstalled()
            },)
    }

    fun uninstallExtension() {
        val extension = extension ?: return
        extensionManager.uninstallExtension(extension.pkgName)
    }

    fun openInSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkgName, null)
        }
        view?.startActivity(intent)
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
