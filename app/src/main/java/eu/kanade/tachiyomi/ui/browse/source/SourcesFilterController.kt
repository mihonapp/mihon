package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.SourcesFilterScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.ComposeController

class SourceFilterController : ComposeController<SourcesFilterPresenter>() {

    override fun getTitle() = resources?.getString(R.string.label_sources)

    override fun createPresenter(): SourcesFilterPresenter = SourcesFilterPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        SourcesFilterScreen(
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
    data class Header(val language: String, val enabled: Boolean) : FilterUiModel()
    data class Item(val source: Source, val enabled: Boolean) : FilterUiModel()
}
