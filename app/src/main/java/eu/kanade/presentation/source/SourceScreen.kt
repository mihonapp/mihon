package eu.kanade.presentation.source

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.browse.source.SourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.UiModel
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun SourceScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: SourcePresenter,
    onClickItem: (Source) -> Unit,
    onClickDisable: (Source) -> Unit,
    onClickLatest: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    val state by presenter.state.collectAsState()

    when {
        state.isLoading -> CircularProgressIndicator()
        state.hasError -> Text(text = state.error!!.message!!)
        state.isEmpty -> EmptyScreen(message = "")
        else -> SourceList(
            nestedScrollConnection = nestedScrollInterop,
            list = state.sources,
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
    list: List<UiModel>,
    onClickItem: (Source) -> Unit,
    onClickDisable: (Source) -> Unit,
    onClickLatest: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
) {
    val (sourceState, setSourceState) = remember { mutableStateOf<Source?>(null) }
    LazyColumn(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(
            items = list,
            contentType = {
                when (it) {
                    is UiModel.Header -> "header"
                    is UiModel.Item -> "item"
                }
            },
            key = {
                when (it) {
                    is UiModel.Header -> it.hashCode()
                    is UiModel.Item -> it.source.key()
                }
            }
        ) { model ->
            when (model) {
                is UiModel.Header -> {
                    SourceHeader(
                        modifier = Modifier.animateItemPlacement(),
                        language = model.language
                    )
                }
                is UiModel.Item -> SourceItem(
                    modifier = Modifier.animateItemPlacement(),
                    item = model.source,
                    onClickItem = onClickItem,
                    onLongClickItem = {
                        setSourceState(it)
                    },
                    onClickLatest = onClickLatest,
                    onClickPin = onClickPin,
                )
            }
        }
    }

    if (sourceState != null) {
        SourceOptionsDialog(
            source = sourceState,
            onClickPin = {
                onClickPin(sourceState)
                setSourceState(null)
            },
            onClickDisable = {
                onClickDisable(sourceState)
                setSourceState(null)
            },
            onDismiss = { setSourceState(null) }
        )
    }
}

@Composable
fun SourceHeader(
    modifier: Modifier = Modifier,
    language: String
) {
    val context = LocalContext.current
    Text(
        text = LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        style = MaterialTheme.typography.header
    )
}

@Composable
fun SourceItem(
    modifier: Modifier = Modifier,
    item: Source,
    onClickItem: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickLatest: (Source) -> Unit,
    onClickPin: (Source) -> Unit
) {
    Row(
        modifier = modifier
            .combinedClickable(
                onClick = { onClickItem(item) },
                onLongClick = { onLongClickItem(item) }
            )
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(source = item)
        Column(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .weight(1f)
        ) {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = LocaleHelper.getDisplayName(item.lang),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (item.supportsLatest) {
            TextButton(onClick = { onClickLatest(item) }) {
                Text(
                    text = stringResource(id = R.string.latest),
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.primary
                    ),
                )
            }
        }
        SourcePinButton(
            isPinned = Pin.Pinned in item.pin,
            onClick = { onClickPin(item) }
        )
    }
}

@Composable
fun SourceIcon(
    source: Source
) {
    val icon = source.icon
    val modifier = Modifier
        .height(40.dp)
        .aspectRatio(1f)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = "",
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(id = R.mipmap.ic_local_source),
            contentDescription = "",
            modifier = modifier,
        )
    }
}

@Composable
fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = "",
            tint = tint
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
                        .padding(vertical = 16.dp)
                )
                if (source.id != LocalSource.ID) {
                    Text(
                        text = stringResource(id = R.string.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}
