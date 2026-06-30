package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onLongClickPin: (Source) -> Unit,
    onRemoveFromGroup: (Source, String) -> Unit,
    onLongClickItem: (Source) -> Unit,
) {
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
                            is SourceUiModel.Item -> "source-${it.sectionKey}-${it.source.key()}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is SourceUiModel.Header -> {
                            SourceHeader(
                                modifier = Modifier.animateItem(),
                                language = model.language,
                                isGroup = model.isGroup,
                            )
                        }
                        is SourceUiModel.Item -> SourceItem(
                            modifier = Modifier.animateItem(),
                            source = model.source,
                            groupSection = model.sectionKey.ifEmpty { null },
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                            onLongClickPin = onLongClickPin,
                            onRemoveFromGroup = onRemoveFromGroup,
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
    isGroup: Boolean = false,
) {
    val context = LocalContext.current
    Text(
        text = if (isGroup) language else LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun SourceItem(
    source: Source,
    groupSection: String?,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickPin: (Source) -> Unit,
    onRemoveFromGroup: (Source, String) -> Unit,
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
                source = source,
                groupSection = groupSection,
                onClickPin = onClickPin,
                onLongClickPin = onLongClickPin,
                onRemoveFromGroup = onRemoveFromGroup,
            )
        },
    )
}

@Composable
private fun SourcePinButton(
    source: Source,
    groupSection: String?,
    onClickPin: (Source) -> Unit,
    onLongClickPin: (Source) -> Unit,
    onRemoveFromGroup: (Source, String) -> Unit,
) {
    val isPinned = Pin.Pinned in source.pin
    val isInGroup = source.pinnedGroups.isNotEmpty()
    val showGroupIcon = groupSection != null || (isInGroup && !isPinned)

    val icon = when {
        showGroupIcon -> Icons.AutoMirrored.Filled.Label
        isPinned -> Icons.Filled.PushPin
        else -> Icons.Outlined.PushPin
    }
    val tint = when {
        showGroupIcon -> MaterialTheme.colorScheme.secondary
        isPinned -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)
    }
    val description = when {
        groupSection != null -> MR.strings.action_remove
        showGroupIcon -> MR.strings.action_pin_groups
        isPinned -> MR.strings.action_unpin
        else -> MR.strings.action_pin
    }
    // In a group section: remove from that group; grouped elsewhere: open dialog; else: toggle pin.
    val onClick: () -> Unit = when {
        groupSection != null -> {
            { onRemoveFromGroup(source, groupSection) }
        }
        showGroupIcon -> {
            { onLongClickPin(source) }
        }
        else -> {
            { onClickPin(source) }
        }
    }

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClickPin(source) },
                role = androidx.compose.ui.semantics.Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
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
    onClickPinGroups: () -> Unit,
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
                Text(
                    text = stringResource(MR.strings.action_pin_groups),
                    modifier = Modifier
                        .clickable(onClick = onClickPinGroups)
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

@Composable
fun SourcePinGroupsDialog(
    pinGroups: Pair<List<String>, List<Boolean>>,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (initialGroups, initialSelections) = pinGroups
    val groups = remember {
        mutableStateListOf<String>().apply { addAll(initialGroups) }
    }
    val selectedIndices = remember {
        mutableStateListOf<Boolean>().apply { addAll(initialSelections) }
    }
    var pinned by remember { mutableStateOf(isPinned) }
    var newGroupName by remember { mutableStateOf("") }
    var createNewGroup by remember { mutableStateOf(false) }

    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.action_pin_groups))
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            pinned = !pinned
                            onTogglePin()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = stringResource(if (pinned) MR.strings.action_unpin else MR.strings.action_pin),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                groups.forEachIndexed { index, group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIndices[index] = !selectedIndices[index]
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedIndices[index],
                            onCheckedChange = null,
                        )
                        Text(
                            text = group,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        IconButton(
                            onClick = {
                                onDeleteGroup(group)
                                groups.removeAt(index)
                                selectedIndices.removeAt(index)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(MR.strings.action_delete),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createNewGroup = !createNewGroup }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = createNewGroup,
                        onCheckedChange = null,
                    )
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = {
                            newGroupName = it
                            createNewGroup = it.isNotBlank()
                        },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .fillMaxWidth(),
                        placeholder = { Text(text = stringResource(MR.strings.action_add_pin_group)) },
                        singleLine = true,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val finalGroups = groups
                        .filterIndexed { index, _ -> selectedIndices[index] }
                        .toMutableSet()

                    if (createNewGroup && newGroupName.isNotBlank()) {
                        finalGroups.add(newGroupName.trim())
                    }
                    onConfirm(finalGroups)
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

sealed interface SourceUiModel {
    data class Item(val source: Source, val sectionKey: String = "") : SourceUiModel
    data class Header(val language: String, val isGroup: Boolean = false) : SourceUiModel
}
