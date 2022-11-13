package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.padding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topSmallPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.browse.source.SourcesPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SourcesScreen(
    presenter: SourcesPresenter,
    contentPadding: PaddingValues,
    onClickItem: (Source, String) -> Unit,
    onClickDisable: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    val context = LocalContext.current
    when {
        presenter.isLoading -> LoadingScreen()
        presenter.isEmpty -> EmptyScreen(
            textResource = R.string.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            SourceList(
                state = presenter,
                contentPadding = contentPadding,
                onClickItem = onClickItem,
                onClickDisable = onClickDisable,
                onClickPin = onClickPin,
            )
        }
    }
    LaunchedEffect(Unit) {
        presenter.events.collectLatest { event ->
            when (event) {
                SourcesPresenter.Event.FailedFetchingSources -> {
                    context.toast(R.string.internal_error)
                }
            }
        }
    }
}

@Composable
private fun SourceList(
    state: SourcesState,
    contentPadding: PaddingValues,
    onClickItem: (Source, String) -> Unit,
    onClickDisable: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        items(
            items = state.items,
            contentType = {
                when (it) {
                    is SourceUiModel.Header -> "header"
                    is SourceUiModel.Item -> "item"
                }
            },
            key = {
                when (it) {
                    is SourceUiModel.Header -> it.hashCode()
                    is SourceUiModel.Item -> "source-${it.source.key()}"
                }
            },
        ) { model ->
            when (model) {
                is SourceUiModel.Header -> {
                    SourceHeader(
                        modifier = Modifier.animateItemPlacement(),
                        language = model.language,
                    )
                }
                is SourceUiModel.Item -> SourceItem(
                    modifier = Modifier.animateItemPlacement(),
                    source = model.source,
                    onClickItem = onClickItem,
                    onLongClickItem = { state.dialog = SourcesPresenter.Dialog(it) },
                    onClickPin = onClickPin,
                )
            }
        }
    }

    if (state.dialog != null) {
        val source = state.dialog!!.source
        SourceOptionsDialog(
            source = source,
            onClickPin = {
                onClickPin(source)
                state.dialog = null
            },
            onClickDisable = {
                onClickDisable(source)
                state.dialog = null
            },
            onDismiss = { state.dialog = null },
        )
    }
}

@Composable
private fun SourceHeader(
    modifier: Modifier = Modifier,
    language: String,
) {
    val context = LocalContext.current
    Text(
        text = LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun SourceItem(
    modifier: Modifier = Modifier,
    source: Source,
    onClickItem: (Source, String) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, GetRemoteManga.QUERY_POPULAR) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onClickItem(source, GetRemoteManga.QUERY_LATEST) }) {
                    Text(
                        text = stringResource(R.string.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            SourcePinButton(
                isPinned = Pin.Pinned in source.pin,
                onClick = { onClickPin(source) },
            )
        },
    )
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    val description = if (isPinned) R.string.action_unpin else R.string.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
private fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) R.string.action_unpin else R.string.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalSource.ID) {
                    Text(
                        text = stringResource(R.string.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed class SourceUiModel {
    data class Item(val source: Source) : SourceUiModel()
    data class Header(val language: String) : SourceUiModel()
}
