package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.isLocal

@Composable
fun SourcesScreen(
    state: SourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickHideLastUsed: () -> Unit,
) {
    var isLastUsedCollapsed by rememberSaveable { mutableStateOf(false) }

    val itemsToDisplay = remember(state.items, isLastUsedCollapsed) {
        if (!isLastUsedCollapsed) return@remember state.items

        var skipping = false
        state.items.filter { model ->
            when (model) {
                is SourceUiModel.Header -> {
                    skipping = model.language == SourcesScreenModel.LAST_USED_KEY
                    true
                }
                is SourceUiModel.Item -> !skipping
            }
        }
    }

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            ScrollbarLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(
                    items = itemsToDisplay,
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
                                modifier = Modifier.animateItem(),
                                language = model.language,
                                onClickHideLastUsed = onClickHideLastUsed,
                                onClickToggleCollapse = {
                                    if (model.language == SourcesScreenModel.LAST_USED_KEY) {
                                        isLastUsedCollapsed = !isLastUsedCollapsed
                                    }
                                },
                            )
                        }
                        is SourceUiModel.Item -> SourceItem(
                            modifier = Modifier.animateItem(),
                            source = model.source,
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHeader(
    language: String,
    modifier: Modifier = Modifier,
    onClickHideLastUsed: () -> Unit,
    onClickToggleCollapse: () -> Unit = {},
) {
    val context = LocalContext.current
    if (language == SourcesScreenModel.LAST_USED_KEY) {
        var isMenuExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClickToggleCollapse)
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = LocaleHelper.getSourceDisplayName(language, context),
                style = MaterialTheme.typography.header,
            )

            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = stringResource(MR.strings.label_more),
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_hide)) },
                        onClick = {
                            isMenuExpanded = false
                            onClickHideLastUsed()
                        },
                    )
                }
            }
        }
    } else {
        Text(
            text = LocaleHelper.getSourceDisplayName(language, context),
            modifier = modifier
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            style = MaterialTheme.typography.header,
        )
    }
}

@Composable
private fun SourceItem(
    source: Source,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
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
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
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
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (!source.isLocal()) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
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

sealed interface SourceUiModel {
    data class Item(val source: Source) : SourceUiModel
    data class Header(val language: String) : SourceUiModel
}
