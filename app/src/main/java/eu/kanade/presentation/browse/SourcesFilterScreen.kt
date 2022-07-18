package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.FilterUiModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SourcesFilterScreen(
    navigateUp: () -> Unit,
    presenter: SourcesFilterPresenter,
    onClickLang: (String) -> Unit,
    onClickSource: (Source) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            AppBar(
                title = stringResource(R.string.label_sources),
                navigateUp = navigateUp,
            )
        },
    ) { paddingValues ->
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.isEmpty -> EmptyScreen(textResource = R.string.source_filter_empty_screen)
            else -> {
                SourcesFilterContent(
                    paddingValues = paddingValues,
                    state = presenter,
                    onClickLang = onClickLang,
                    onClickSource = onClickSource,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        presenter.events.collectLatest { event ->
            when (event) {
                SourcesFilterPresenter.Event.FailedFetchingLanguages -> {
                    context.toast(R.string.internal_error)
                }
            }
        }
    }
}

@Composable
fun SourcesFilterContent(
    paddingValues: PaddingValues,
    state: SourcesFilterState,
    onClickLang: (String) -> Unit,
    onClickSource: (Source) -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = paddingValues + WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(
            items = state.items,
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
            },
        ) { model ->
            when (model) {
                is FilterUiModel.Header -> {
                    SourcesFilterHeader(
                        modifier = Modifier.animateItemPlacement(),
                        language = model.language,
                        enabled = model.enabled,
                        onClickItem = onClickLang,
                    )
                }
                is FilterUiModel.Item -> SourcesFilterItem(
                    modifier = Modifier.animateItemPlacement(),
                    source = model.source,
                    enabled = model.enabled,
                    onClickItem = onClickSource,
                )
            }
        }
    }
}

@Composable
fun SourcesFilterHeader(
    modifier: Modifier,
    language: String,
    enabled: Boolean,
    onClickItem: (String) -> Unit,
) {
    PreferenceRow(
        modifier = modifier,
        title = LocaleHelper.getSourceDisplayName(language, LocalContext.current),
        action = {
            Switch(checked = enabled, onCheckedChange = null)
        },
        onClick = { onClickItem(language) },
    )
}

@Composable
fun SourcesFilterItem(
    modifier: Modifier,
    source: Source,
    enabled: Boolean,
    onClickItem: (Source) -> Unit,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        showLanguageInContent = false,
        onClickItem = { onClickItem(source) },
        action = {
            Checkbox(checked = enabled, onCheckedChange = null)
        },
    )
}
