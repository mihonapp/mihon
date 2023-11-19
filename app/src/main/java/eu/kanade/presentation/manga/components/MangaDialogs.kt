package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DeleteChaptersDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.confirm_delete_chapters))
        },
    )
}

@Composable
fun SetIntervalDialog(
    interval: Int,
    onDismissRequest: () -> Unit,
    onValueChanged: (Int) -> Unit,
) {
    var selectedInterval by rememberSaveable { mutableIntStateOf(if (interval < 0) -interval else 0) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.manga_modify_calculated_interval_title)) },
        text = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val size = DpSize(width = maxWidth / 2, height = 128.dp)
                val items = (0..FetchInterval.MAX_INTERVAL)
                    .map {
                        if (it == 0) {
                            stringResource(MR.strings.label_default)
                        } else {
                            it.toString()
                        }
                    }
                    .toImmutableList()
                WheelTextPicker(
                    items = items,
                    size = size,
                    startIndex = selectedInterval,
                    onSelectionChanged = { selectedInterval = it },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValueChanged(selectedInterval)
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
