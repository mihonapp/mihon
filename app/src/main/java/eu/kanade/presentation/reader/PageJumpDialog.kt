package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelNumberPicker
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onConfirm: (pageIndex: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selectedPageIndex by remember(currentPage, totalPages) { mutableIntStateOf(currentPage - 1) }
    val pages = remember(totalPages) { (1..totalPages).toList() }

    AlertDialog(
        title = { Text(stringResource(MR.strings.jump_to_page)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                WheelNumberPicker(
                    items = pages,
                    startIndex = currentPage - 1,
                    onSelectionChanged = { selectedPageIndex = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedPageIndex) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
