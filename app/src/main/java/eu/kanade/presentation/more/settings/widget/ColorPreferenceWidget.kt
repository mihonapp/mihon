package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.components.ColorPicker
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun ColorPreferenceWidget(
    title: String,
    preference: Preference<Int>,
) {
    val colorInt by preference.collectAsState()
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = String.format("#%08X", colorInt),
        widget = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
            )
        },
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        val scope = rememberCoroutineScope()
        val onDismissRequest = { isDialogShown = false }
        var isVisualPicker by remember { mutableStateOf(false) }
        var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(String.format("%08X", colorInt)))
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = {
                if (isVisualPicker) {
                    ColorPicker(
                        initialColor = Color(colorInt),
                        onColorChanged = {
                            textFieldValue = TextFieldValue(String.format("%08X", it.toArgb()))
                        },
                    )
                } else {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        trailingIcon = {
                            if (textFieldValue.text.isBlank()) {
                                Icon(imageVector = Icons.Filled.Error, contentDescription = null)
                            } else {
                                IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                                    Icon(imageVector = Icons.Filled.Cancel, contentDescription = null)
                                }
                            }
                        },
                        isError = textFieldValue.text.isBlank(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    enabled = textFieldValue.text.isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                val newColor = textFieldValue.text.toLong(16).toInt()
                                preference.set(newColor)
                                onDismissRequest()
                            } catch (_: Exception) {
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { isVisualPicker = !isVisualPicker }) {
                        Text(text = if (isVisualPicker) "Hex" else "Visual")
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                }
            },
        )
    }
}
