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
    val items = remember(availableScanlators, scanlatorFilter) {
        val visibleFilters = scanlatorFilter.filter { it.priority != ScanlatorFilter.EXCLUDED }.sortedBy { it.priority }
        val hiddenFilters = scanlatorFilter.filter { it.priority == ScanlatorFilter.EXCLUDED }

        val visibleScanlators = visibleFilters.map { it.scanlator ?: "" }
        val hiddenScanlators = hiddenFilters.map { it.scanlator ?: "" }

        val knownScanlators = visibleScanlators.toSet() + hiddenScanlators.toSet()
        val newScanlators = availableScanlators.filter {
            it !in knownScanlators
        }.sortedWith(String.CASE_INSENSITIVE_ORDER)

        val list = mutableListOf<ScanlatorUiModel>()
        visibleFilters.forEach { list.add(ScanlatorUiModel(it.scanlator ?: "", false)) }
        newScanlators.forEach { list.add(ScanlatorUiModel(it, false)) }
        hiddenFilters.forEach { list.add(ScanlatorUiModel(it.scanlator ?: "", true)) }

        list.toMutableStateList()
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
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
                        ReorderableItem(reorderableState, key = item.name) { isDragging ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (item.hidden) 0.38f else 1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(),
                                ) {
                                    Icon(Icons.Rounded.DragHandle, contentDescription = null)
                                }

                                Text(
                                    text = if (item.name.isEmpty()) stringResource(MR.strings.scanlator) else item.name,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                IconButton(onClick = {
                                    item.hidden = !item.hidden
                                    items.remove(item)
                                    if (item.hidden) {
                                        items.add(item)
                                    } else {
                                        val firstHiddenIndex = items.indexOfFirst { it.hidden }
                                        if (firstHiddenIndex != -1) {
                                            items.add(firstHiddenIndex, item)
                                        } else {
                                            items.add(item)
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (item.hidden) {
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
                        val result = items.mapIndexed { index, item ->
                            ScanlatorFilter(
                                scanlator = item.name.ifEmpty { null },
                                priority = if (item.hidden) ScanlatorFilter.EXCLUDED else index,
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
    initialHidden: Boolean,
) {
    var hidden by mutableStateOf(initialHidden)
}
