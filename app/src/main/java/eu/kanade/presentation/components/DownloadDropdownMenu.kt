package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R

@Composable
fun DownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    includeDownloadAllOption: Boolean = true,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.download_1)) },
            onClick = {
                onDownloadClicked(DownloadAction.NEXT_1_CHAPTER)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.download_5)) },
            onClick = {
                onDownloadClicked(DownloadAction.NEXT_5_CHAPTERS)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.download_10)) },
            onClick = {
                onDownloadClicked(DownloadAction.NEXT_10_CHAPTERS)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.download_custom)) },
            onClick = {
                onDownloadClicked(DownloadAction.CUSTOM)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.download_unread)) },
            onClick = {
                onDownloadClicked(DownloadAction.UNREAD_CHAPTERS)
                onDismissRequest()
            },
        )
        if (includeDownloadAllOption) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.download_all)) },
                onClick = {
                    onDownloadClicked(DownloadAction.ALL_CHAPTERS)
                    onDismissRequest()
                },
            )
        }
    }
}
