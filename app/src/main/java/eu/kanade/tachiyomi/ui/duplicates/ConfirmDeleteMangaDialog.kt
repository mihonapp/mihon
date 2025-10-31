package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ConfirmDeleteMangaDialog(
    isLocalManga: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (deleteDownloads: Boolean) -> Unit,
) {
    var checkBoxState: CheckboxState.State<StringResource> by remember {
        mutableStateOf(CheckboxState.State.None(MR.strings.downloaded_chapters))
    }
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
                    onConfirm(checkBoxState.isChecked)
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_remove))
        },
        text = {
            if (!isLocalManga) {
                Column {
                    LabeledCheckbox(
                        label = stringResource(MR.strings.downloaded_chapters),
                        checked = checkBoxState.isChecked,
                        onCheckedChange = {
                            checkBoxState = checkBoxState.next() as CheckboxState.State<StringResource>
                        },
                    )
                }
            }
        },
    )
}
