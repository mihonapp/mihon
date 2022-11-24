package eu.kanade.tachiyomi.ui.browse

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController

class BrowseController : BasicFullComposeController, RootController {

    @Suppress("unused")
    constructor(bundle: Bundle? = null) : this(bundle?.getBoolean(TO_EXTENSIONS_EXTRA) ?: false)

    constructor(toExtensions: Boolean = false) : super(
        bundleOf(TO_EXTENSIONS_EXTRA to toExtensions),
    )

    private val toExtensions = args.getBoolean(TO_EXTENSIONS_EXTRA, false)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = BrowseScreen(toExtensions = toExtensions))
    }
}

private const val TO_EXTENSIONS_EXTRA = "to_extensions"
