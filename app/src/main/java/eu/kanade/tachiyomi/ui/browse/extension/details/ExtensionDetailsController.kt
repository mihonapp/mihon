package eu.kanade.tachiyomi.ui.browse.extension.details

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController

@SuppressLint("RestrictedApi")
class ExtensionDetailsController(
    bundle: Bundle? = null,
) : FullComposeController<ExtensionDetailsPresenter>(bundle) {

    constructor(pkgName: String) : this(
        bundleOf(PKGNAME_KEY to pkgName),
    )

    override fun createPresenter() = ExtensionDetailsPresenter(args.getString(PKGNAME_KEY)!!)

    @Composable
    override fun ComposeContent() {
        ExtensionDetailsScreen(
            navigateUp = router::popCurrentController,
            presenter = presenter,
            onClickSourcePreferences = { router.pushController(SourcePreferencesController(it)) },
        )
    }

    fun onExtensionUninstalled() {
        router.popCurrentController()
    }
}

private const val PKGNAME_KEY = "pkg_name"
