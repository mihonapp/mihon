package eu.kanade.tachiyomi.ui.browse.extension.details

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

private const val PKGNAME_KEY = "pkg_name"

class ExtensionDetailsController : BasicFullComposeController {

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getString(PKGNAME_KEY)!!)

    constructor(pkgName: String) : super(bundleOf(PKGNAME_KEY to pkgName))

    val pkgName: String
        get() = args.getString(PKGNAME_KEY)!!

    @Composable
    override fun ComposeContent() {
        Navigator(screen = ExtensionDetailsScreen(pkgName = pkgName))
    }
}
