package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

class ExtensionFilterController : FullComposeController<ExtensionFilterPresenter>() {

    override fun createPresenter() = ExtensionFilterPresenter()

    @Composable
    override fun ComposeContent() {
        ExtensionFilterScreen(
            navigateUp = router::popCurrentController,
            presenter = presenter,
        )
    }
}

data class FilterUiModel(val lang: String, val enabled: Boolean)
