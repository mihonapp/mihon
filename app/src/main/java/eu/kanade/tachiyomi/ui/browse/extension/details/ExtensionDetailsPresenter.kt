package eu.kanade.tachiyomi.ui.browse.extension.details

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.injectLazy

class ExtensionDetailsPresenter(
    private val controller: ExtensionDetailsController,
    private val pkgName: String,
) : BasePresenter<ExtensionDetailsController>() {

    private val extensionManager: ExtensionManager by injectLazy()

    val extension = extensionManager.installedExtensions.find { it.pkgName == pkgName }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        bindToUninstalledExtension()
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
            })
    }

    fun uninstallExtension() {
        val extension = extension ?: return
        extensionManager.uninstallExtension(extension.pkgName)
    }

    fun openInSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkgName, null)
        }
        controller.startActivity(intent)
    }
}
