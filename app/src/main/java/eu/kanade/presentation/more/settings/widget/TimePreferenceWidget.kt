package eu.kanade.presentation.more.settings.widget

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TimePreferenceWidget(
    title: String,
    subtitle: String?,
    value: LocalTime,
    onConfirm: suspend (String) -> Boolean
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle?.format(value),
        onPreferenceClick = { isDialogShown = true },
    )
    var colors = TimePickerDefaults.colors(periodSelectorSelectedContainerColor = TimePickerDefaults.colors().timeSelectorSelectedContainerColor, periodSelectorSelectedContentColor = TimePickerDefaults.colors().timeSelectorSelectedContentColor);
    if (isDialogShown) {
        val scope = rememberCoroutineScope()
        val onDismissRequest = { isDialogShown = false }

        val context = LocalContext.current
        val timePickerState = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = {
                TimeInput(
                    state = timePickerState,
                    colors = colors
                )
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    enabled = true,
                    onClick = {
                        scope.launch {
                            val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                            val format = DateTimeFormatter.ofPattern("h:mm a")
                            val timeStr = time.format(format)
                            if (onConfirm(timeStr)) {
                                onDismissRequest()
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
