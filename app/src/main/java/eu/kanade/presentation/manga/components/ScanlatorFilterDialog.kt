package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.ScanlatorFilter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: Set<String>,
    scanlatorFilter: List<ScanlatorFilter>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<ScanlatorFilter>) -> Unit,
) {
    val items = remember(scanlatorFilter, availableScanlators) {
        if (scanlatorFilter.isEmpty()) {
            availableScanlators
                .mapIndexed { index, scanlator -> ScanlatorUiModel(scanlator, index, false) }
                .toMutableStateList()
        } else {
            scanlatorFilter
                .sortedWith(
                    compareBy<ScanlatorFilter> { it.excluded }
                        .thenBy { it.priority },
                )
                .map { ScanlatorUiModel(it.scanlator ?: "", it.priority, it.excluded) }
                .toMutableStateList()
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Swap priorities to preserve the set of values
        val fromItem = items[from.index]
        val toItem = items[to.index]
        val tmp = fromItem.priority
        fromItem.priority = toItem.priority
        toItem.priority = tmp

        items.add(to.index, items.removeAt(from.index))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.filter_scanlators)) },
        text = {
            if (items.isEmpty()) {
                Text(text = stringResource(MR.strings.no_scanlators_found))
            } else {
                ScrollbarLazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.name }) { item ->
                        ReorderableItem(reorderableState, key = item.name, enabled = !item.excluded) { _ ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (item.excluded) 0.38f else 1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(),
                                    enabled = !item.excluded,
                                ) {
                                    Icon(Icons.Rounded.DragHandle, contentDescription = null)
                                }

                                Text(
                                    text = item.name.ifEmpty { stringResource(MR.strings.unknown_scanlator) },
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                IconButton(onClick = {
                                    item.excluded = !item.excluded
                                    items.remove(item)
                                    // Find insertion index based on sort order (Excluded, Priority)
                                    val insertIndex = items.indexOfFirst {
                                        if (item.excluded) {
                                            it.excluded && it.priority > item.priority
                                        } else {
                                            it.excluded || it.priority > item.priority
                                        }
                                    }.takeIf { it != -1 } ?: items.size
                                    items.add(insertIndex, item)
                                }) {
                                    Icon(
                                        imageVector = if (item.excluded) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (scanlatorFilter.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onConfirm(emptyList())
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_reset))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(
                    onClick = {
                        val result = items.map { item ->
                            ScanlatorFilter(
                                scanlator = item.name.ifEmpty { null },
                                priority = item.priority,
                                excluded = item.excluded,
                            )
                        }
                        onConfirm(result)
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_apply))
                }
            }
        },
    )
}

class ScanlatorUiModel(
    val name: String,
    initialPriority: Int,
    initialExcluded: Boolean,
) {
    var priority by mutableIntStateOf(initialPriority)
    var excluded by mutableStateOf(initialExcluded)
}
