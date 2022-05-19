package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.browse.source.SourceState
import eu.kanade.tachiyomi.ui.browse.source.SourcesPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun SourcesScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: SourcesPresenter,
    onClickItem: (Source) -> Unit,
    onClickDisable: (Source) -> Unit,
    onClickLatest: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    val state by presenter.state.collectAsState()

    when (state) {
        is SourceState.Loading -> LoadingScreen()
        is SourceState.Error -> Text(text = (state as SourceState.Error).error.message!!)
        is SourceState.Success -> SourceList(
            nestedScrollConnection = nestedScrollInterop,
            list = (state as SourceState.Success).uiModels,
            onClickItem = onClickItem,
            onClickDisable = onClickDisable,
            onClickLatest = onClickLatest,
            onClickPin = onClickPin,
        )
    }
}

@Composable
fun SourceList(
    nestedScrollConnection: NestedScrollConnection,
    list: List<SourceUiModel>,
    onClickItem: (Source) -> Unit,
    onClickDisable: (Source) -> Unit,
    onClickLatest: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    if (list.isEmpty()) {
        EmptyScreen(textResource = R.string.source_empty_screen)
        return
    }

    var sourceState by remember { mutableStateOf<Source?>(null) }

    LazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        contentPadding = WindowInsets.navigationBars.asPaddingValues() + topPaddingValues,
    ) {
        items(
            items = list,
            contentType = {
                when (it) {
                    is SourceUiModel.Header -> "header"
                    is SourceUiModel.Item -> "item"
                }
            },
            key = {
                when (it) {
                    is SourceUiModel.Header -> it.hashCode()
                    is SourceUiModel.Item -> it.source.key()
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
                    onLongClickItem = { sourceState = it },
                    onClickLatest = onClickLatest,
                    onClickPin = onClickPin,
                )
            }
        }
    }

    if (sourceState != null) {
        SourceOptionsDialog(
            source = sourceState!!,
            onClickPin = {
                onClickPin(sourceState!!)
                sourceState = null
            },
            onClickDisable = {
                onClickDisable(sourceState!!)
                sourceState = null
            },
            onDismiss = { sourceState = null },
        )
    }
}

@Composable
fun SourceHeader(
    modifier: Modifier = Modifier,
    language: String,
) {
    val context = LocalContext.current
    Text(
        text = LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        style = MaterialTheme.typography.header,
    )
}

@Composable
fun SourceItem(
    modifier: Modifier = Modifier,
    source: Source,
    onClickItem: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickLatest: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source) },
        onLongClickItem = { onLongClickItem(source) },
        action = { source ->
            if (source.supportsLatest) {
                TextButton(onClick = { onClickLatest(source) }) {
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
fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = "",
            tint = tint,
        )
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.nameWithLanguage)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) R.string.action_unpin else R.string.action_pin
                Text(
                    text = stringResource(id = textId),
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
