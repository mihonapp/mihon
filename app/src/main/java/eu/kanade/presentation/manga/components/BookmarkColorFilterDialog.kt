package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BookmarkColorFilterDialog(
    includedColors: Set<BookmarkColor>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<BookmarkColor>) -> Unit,
) {
    val colors = remember { BookmarkColor.entries }
    val selected = remember(includedColors) {
        val allSelected = includedColors.isEmpty() || includedColors.size == colors.size
        colors.map { allSelected || it in includedColors }.toMutableStateList()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.action_filter_bookmark_colors)) },
        text = {
            Box {
                val state = rememberLazyListState()
                LazyColumn(state = state) {
                    itemsIndexed(colors) { index, color ->
                        val isSelected = selected[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { selected[index] = !isSelected }
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.small)
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = if (isSelected) {
                                    Icons.Rounded.CheckBox
                                } else {
                                    Icons.Rounded.CheckBoxOutlineBlank
                                },
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                                contentDescription = stringResource(
                                    if (isSelected) MR.strings.selected else MR.strings.not_selected,
                                ),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color.asComposeColor()),
                            )
                            Text(
                                text = stringResource(color.titleRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
                if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            FlowRow {
                if (selected.all { it }) {
                    TextButton(onClick = { selected.indices.forEach { selected[it] = false } }) {
                        Text(text = stringResource(MR.strings.action_disable_all))
                    }
                } else {
                    TextButton(onClick = { selected.indices.forEach { selected[it] = true } }) {
                        Text(text = stringResource(MR.strings.action_select_all))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(
                    onClick = {
                        onConfirm(colors.filterIndexed { index, _ -> selected[index] }.toSet())
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    )
}
