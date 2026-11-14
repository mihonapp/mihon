package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun LibraryPageJumpDialog(
    page: Int,
    pageCount: Int,
    onSelectPage: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var input by remember {
        mutableStateOf(TextFieldValue((page + 1).toString(), TextRange(0, (page + 1).toString().length)))
    }
    val selectedPage = libraryPageIndex(input.text, pageCount)
    val onConfirm = {
        if (selectedPage != null) {
            onSelectPage(selectedPage)
            onDismissRequest()
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.library_jump_to_page)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(MR.strings.library_page_input, pageCount)) },
                    singleLine = true,
                    isError = input.text.isNotEmpty() && selectedPage == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                )
                LibraryPageSlider(
                    page = selectedPage ?: page,
                    pageCount = pageCount,
                    onValueChange = { input = TextFieldValue((it + 1).toString()) },
                    onValueChangeFinished = {},
                    showNumbers = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selectedPage != null) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onSelectPage(0)
                    onDismissRequest()
                }) {
                    Text(stringResource(MR.strings.library_jump_to_top))
                }
                TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
            }
        },
    )
}

internal fun libraryPageIndex(input: String, pageCount: Int): Int? =
    input.toIntOrNull()?.takeIf { it in 1..pageCount }?.minus(1)
