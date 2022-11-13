package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.commandiron.wheel_picker_compose.WheelDatePicker
import com.commandiron.wheel_picker_compose.WheelTextPicker
import eu.kanade.presentation.components.AlertDialogContent
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.isScrolledToEnd
import eu.kanade.presentation.util.isScrolledToStart
import eu.kanade.presentation.util.minimumTouchTargetSize
import eu.kanade.tachiyomi.R
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun TrackStatusSelector(
    contentPadding: PaddingValues,
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    selections: Map<Int, String>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        contentPadding = contentPadding,
        title = stringResource(R.string.status),
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
                                .minimumTouchTargetSize(),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyLarge.merge(),
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        }
                    }
                }
            }
            if (!state.isScrolledToStart()) Divider(modifier = Modifier.align(Alignment.TopCenter))
            if (!state.isScrolledToEnd()) Divider(modifier = Modifier.align(Alignment.BottomCenter))
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackChapterSelector(
    contentPadding: PaddingValues,
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    range: Iterable<Int>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        contentPadding = contentPadding,
        title = stringResource(R.string.chapters),
        content = {
            WheelTextPicker(
                modifier = Modifier.align(Alignment.Center),
                texts = range.map { "$it" },
                onScrollFinished = {
                    onSelectionChange(it)
                    null
                },
                startIndex = selection,
            )
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackScoreSelector(
    contentPadding: PaddingValues,
    selection: String,
    onSelectionChange: (String) -> Unit,
    selections: List<String>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        contentPadding = contentPadding,
        title = stringResource(R.string.score),
        content = {
            WheelTextPicker(
                modifier = Modifier.align(Alignment.Center),
                texts = selections,
                onScrollFinished = {
                    onSelectionChange(selections[it])
                    null
                },
                startIndex = selections.indexOf(selection).coerceAtLeast(0),
            )
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun TrackDateSelector(
    contentPadding: PaddingValues,
    title: String,
    selection: LocalDate,
    onSelectionChange: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onRemove: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    BaseSelector(
        contentPadding = contentPadding,
        title = title,
        content = {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var internalSelection by remember { mutableStateOf(selection) }
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                    text = internalSelection.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, java.util.Locale.getDefault()),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                WheelDatePicker(
                    startDate = selection,
                    onScrollFinished = {
                        internalSelection = it
                        onSelectionChange(it)
                    },
                )
            }
        },
        thirdButton = if (onRemove != null) {
            {
                TextButton(onClick = onRemove) {
                    Text(text = stringResource(R.string.action_remove))
                }
            }
        } else {
            null
        },
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun BaseSelector(
    contentPadding: PaddingValues = PaddingValues(),
    title: String,
    content: @Composable BoxScope.() -> Unit,
    thirdButton: @Composable (RowScope.() -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialogContent(
        modifier = Modifier.padding(contentPadding),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (thirdButton != null) {
                    thirdButton()
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        },
    )
}
