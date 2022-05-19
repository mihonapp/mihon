package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.ComposeController

class ExtensionFilterController : ComposeController<ExtensionFilterPresenter>() {

    override fun getTitle() = resources?.getString(R.string.label_extensions)

    override fun createPresenter(): ExtensionFilterPresenter = ExtensionFilterPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        ExtensionFilterScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickLang = { language ->
                presenter.toggleLanguage(language)
            },
        )
    }
}

data class FilterUiModel(val lang: String, val enabled: Boolean)
