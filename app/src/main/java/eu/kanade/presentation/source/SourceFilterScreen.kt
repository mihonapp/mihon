package eu.kanade.presentation.source

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.source.components.BaseSourceItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.FilterUiModel
import eu.kanade.tachiyomi.ui.browse.source.SourceFilterPresenter
import eu.kanade.tachiyomi.ui.browse.source.SourceFilterState
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun SourceFilterScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: SourceFilterPresenter,
    onClickLang: (String) -> Unit,
    onClickSource: (Source) -> Unit
) {
    val state by presenter.state.collectAsState()

    when (state) {
        is SourceFilterState.Loading -> LoadingScreen()
        is SourceFilterState.Error -> Text(text = (state as SourceFilterState.Error).error!!.message!!)
        is SourceFilterState.Success ->
            SourceFilterContent(
                nestedScrollInterop = nestedScrollInterop,
                items = (state as SourceFilterState.Success).models,
                onClickLang = onClickLang,
                onClickSource = onClickSource,
            )
    }
}

@Composable
fun SourceFilterContent(
    nestedScrollInterop: NestedScrollConnection,
    items: List<FilterUiModel>,
    onClickLang: (String) -> Unit,
    onClickSource: (Source) -> Unit
) {
    if (items.isEmpty()) {
        EmptyScreen(textResource = R.string.source_filter_empty_screen)
        return
    }
    LazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(
            items = items,
            contentType = {
                when (it) {
                    is FilterUiModel.Header -> "header"
                    is FilterUiModel.Item -> "item"
                }
            },
            key = {
                when (it) {
                    is FilterUiModel.Header -> it.hashCode()
                    is FilterUiModel.Item -> it.source.key()
                }
            }
        ) { model ->
            when (model) {
                is FilterUiModel.Header -> {
                    SourceFilterHeader(
                        modifier = Modifier.animateItemPlacement(),
                        language = model.language,
                        isEnabled = model.isEnabled,
                        onClickItem = onClickLang
                    )
                }
                is FilterUiModel.Item -> SourceFilterItem(
                    modifier = Modifier.animateItemPlacement(),
                    source = model.source,
                    isEnabled = model.isEnabled,
                    onClickItem = onClickSource
                )
            }
        }
    }
}

@Composable
fun SourceFilterHeader(
    modifier: Modifier,
    language: String,
    isEnabled: Boolean,
    onClickItem: (String) -> Unit
) {
    PreferenceRow(
        modifier = modifier,
        title = LocaleHelper.getSourceDisplayName(language, LocalContext.current),
        action = {
            Switch(checked = isEnabled, onCheckedChange = null)
        },
        onClick = { onClickItem(language) },
    )
}

@Composable
fun SourceFilterItem(
    modifier: Modifier,
    source: Source,
    isEnabled: Boolean,
    onClickItem: (Source) -> Unit
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        showLanguageInContent = false,
        onClickItem = { onClickItem(source) },
        action = {
            Checkbox(checked = isEnabled, onCheckedChange = null)
        }
    )
}
