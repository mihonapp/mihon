package eu.kanade.presentation.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.WheelNumberPicker
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.isScrolledToEnd
import tachiyomi.presentation.core.util.isScrolledToStart

@Composable
fun TrackStatusSelector(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    selections: Map<Int, StringResource?>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        title = stringResource(MR.strings.status),
        content = {
            val state = rememberLazyListState()
            ScrollbarLazyColumn(state = state) {
                selections.forEach { (key, value) ->
                    val isSelected = selection == key
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .selectable(
                                    selected = isSelected,
                                    onClick = { onSelectionChange(key) },
                                )
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                            Text(
                                text = value?.let { stringResource(it) } ?: "",
                                style = MaterialTheme.typography.bodyLarge.merge(),
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        }
                    }
                }
            }
            if (!state.isScrolledToStart()) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
            if (!state.isScrolledToEnd()) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackChapterSelector(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    range: Iterable<Int>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        title = stringResource(MR.strings.chapters),
        content = {
            WheelNumberPicker(
                items = range.toImmutableList(),
                modifier = Modifier.align(Alignment.Center),
                startIndex = selection,
                onSelectionChanged = { onSelectionChange(it) },
            )
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackScoreSelector(
    selection: String,
    onSelectionChange: (String) -> Unit,
    selections: ImmutableList<String>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        title = stringResource(MR.strings.score),
        content = {
            WheelTextPicker(
                items = selections,
                modifier = Modifier.align(Alignment.Center),
                startIndex = selections.indexOf(selection).takeIf { it > 0 } ?: (selections.size / 2),
                onSelectionChanged = { onSelectionChange(selections[it]) },
            )
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackDateSelector(
    title: String,
    initialSelectedDateMillis: Long,
    selectableDates: SelectableDates,
    onConfirm: (Long) -> Unit,
    onRemove: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis,
        selectableDates = selectableDates,
    )
    AlertDialogContent(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        title = { Text(text = title) },
        content = {
            Column {
                DatePicker(
                    state = pickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
                ) {
                    if (onRemove != null) {
                        TextButton(onClick = onRemove) {
                            Text(text = stringResource(MR.strings.action_remove))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    TextButton(onClick = { onConfirm(pickerState.selectedDateMillis!!) }) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        },
    )
}

@Composable
private fun BaseSelector(
    title: String,
    content: @Composable BoxScope.() -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    thirdButton: @Composable (RowScope.() -> Unit)? = null,
) {
    AlertDialogContent(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        title = { Text(text = title) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
            ) {
                if (thirdButton != null) {
                    thirdButton()
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun TrackStatusSelectorPreviews() {
    TachiyomiPreviewTheme {
        Surface {
            TrackStatusSelector(
                selection = 1,
                onSelectionChange = {},
                selections = persistentMapOf(
                    // Anilist values
                    1 to MR.strings.reading,
                    2 to MR.strings.plan_to_read,
                    3 to MR.strings.completed,
                    4 to MR.strings.on_hold,
                    5 to MR.strings.dropped,
                    6 to MR.strings.repeating,
                ),
                onConfirm = {},
                onDismissRequest = {},
            )
        }
    }
}
