package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CheckBoxOutlineBlank
import mihon.icons.materialsymbols.roundedfilled.CheckBox
import mihon.icons.materialsymbols.roundedfilled.DisabledByDefault
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: Set<String>,
    filteredScanlators: Set<String>,
    include: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (scanlators: Set<String>, include: Boolean) -> Unit,
) {
    val sortedAvailableScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val checkedScanlators = remember(filteredScanlators) { filteredScanlators.toMutableStateList() }
    var includeChecked by remember(include) { mutableStateOf(include) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(
                    if (includeChecked) MR.strings.include_scanlators else MR.strings.exclude_scanlators,
                ),
            )
        },
        text = textFunc@{
            if (sortedAvailableScanlators.isEmpty()) {
                Text(text = stringResource(MR.strings.no_scanlators_found))
                return@textFunc
            }
            Column {
                FilterModeSelector(
                    include = includeChecked,
                    onIncludeChanged = { includeChecked = it },
                )
                ScanlatorList(
                    scanlators = sortedAvailableScanlators,
                    checkedScanlators = checkedScanlators,
                    checkedIcon = if (includeChecked) {
                        MaterialSymbols.RoundedFilled.CheckBox
                    } else {
                        MaterialSymbols.RoundedFilled.DisabledByDefault
                    },
                )
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            if (sortedAvailableScanlators.isEmpty()) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            } else {
                FlowRow {
                    if (checkedScanlators.isEmpty()) {
                        TextButton(onClick = { checkedScanlators.addAll(availableScanlators) }) {
                            Text(text = stringResource(MR.strings.action_select_all))
                        }
                    } else {
                        TextButton(onClick = checkedScanlators::clear) {
                            Text(text = stringResource(MR.strings.action_reset))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(checkedScanlators.toSet(), includeChecked)
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        },
    )
}

@Composable
private fun FilterModeSelector(
    include: Boolean,
    onIncludeChanged: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.padding.small),
    ) {
        SegmentedButton(
            selected = !include,
            onClick = { onIncludeChanged(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) {
            Text(text = stringResource(MR.strings.scanlator_filter_exclude))
        }
        SegmentedButton(
            selected = include,
            onClick = { onIncludeChanged(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) {
            Text(text = stringResource(MR.strings.scanlator_filter_include))
        }
    }
}

@Composable
private fun ScanlatorList(
    scanlators: List<String>,
    checkedScanlators: MutableList<String>,
    checkedIcon: ImageVector,
) {
    Box {
        val state = rememberLazyListState()
        LazyColumn(state = state) {
            scanlators.forEach { scanlator ->
                item {
                    val isChecked = scanlator in checkedScanlators
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                if (isChecked) {
                                    checkedScanlators.remove(scanlator)
                                } else {
                                    checkedScanlators.add(scanlator)
                                }
                            }
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.small)
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.small),
                    ) {
                        Icon(
                            imageVector = if (isChecked) {
                                checkedIcon
                            } else {
                                MaterialSymbols.Rounded.CheckBoxOutlineBlank
                            },
                            tint = if (isChecked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                            contentDescription = null,
                        )
                        Text(
                            text = scanlator,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
            }
        }
        if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
        if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
