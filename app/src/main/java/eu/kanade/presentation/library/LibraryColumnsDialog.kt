package eu.kanade.presentation.library

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.WheelPickerDefaults
import tachiyomi.presentation.core.components.WheelTextPicker

@Composable
fun LibraryColumnsDialog(
    initialPortrait: Int,
    initialLandscape: Int,
    onDismissRequest: () -> Unit,
    onValueChanged: (portrait: Int, landscape: Int) -> Unit,
) {
    var portraitValue by rememberSaveable { mutableStateOf(initialPortrait) }
    var landscapeValue by rememberSaveable { mutableStateOf(initialLandscape) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.pref_library_columns)) },
        text = {
            Column {
                Row {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.portrait),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.landscape),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                LibraryColumnsPicker(
                    modifier = Modifier.fillMaxWidth(),
                    portraitValue = portraitValue,
                    onPortraitChange = { portraitValue = it },
                    landscapeValue = landscapeValue,
                    onLandscapeChange = { landscapeValue = it },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = portraitValue != initialPortrait || landscapeValue != initialLandscape,
                onClick = { onValueChanged(portraitValue, landscapeValue) },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun LibraryColumnsPicker(
    modifier: Modifier = Modifier,
    portraitValue: Int,
    onPortraitChange: (Int) -> Unit,
    landscapeValue: Int,
    onLandscapeChange: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        WheelPickerDefaults.Background(size = DpSize(maxWidth, 128.dp))

        val size = DpSize(width = maxWidth / 2, height = 128.dp)
        Row {
            val columns = (0..10).map { getColumnValue(value = it) }
            WheelTextPicker(
                startIndex = portraitValue,
                items = columns,
                size = size,
                onSelectionChanged = onPortraitChange,
                backgroundContent = null,
            )
            WheelTextPicker(
                startIndex = landscapeValue,
                items = columns,
                size = size,
                onSelectionChanged = onLandscapeChange,
                backgroundContent = null,
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun getColumnValue(value: Int): String {
    return if (value == 0) {
        stringResource(R.string.label_default)
    } else {
        value.toString()
    }
}
