package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.source.SourceFilterScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.ComposeController

class SourceFilterController : ComposeController<SourceFilterPresenter>() {

    override fun getTitle() = resources?.getString(R.string.label_sources)

    override fun createPresenter(): SourceFilterPresenter = SourceFilterPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        SourceFilterScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickLang = { language ->
                presenter.toggleLanguage(language)
            },
            onClickSource = { source ->
                presenter.toggleSource(source)
            },
        )
    }
}

sealed class FilterUiModel {
    data class Header(val language: String, val isEnabled: Boolean) : FilterUiModel()
    data class Item(val source: Source, val isEnabled: Boolean) : FilterUiModel()
}
