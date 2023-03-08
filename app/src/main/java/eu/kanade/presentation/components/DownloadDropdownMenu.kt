package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R

@Composable
fun DownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        listOfNotNull(
            DownloadAction.NEXT_1_CHAPTER to pluralStringResource(R.plurals.download_amount, 1, 1),
            DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(R.plurals.download_amount, 5, 5),
            DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(R.plurals.download_amount, 10, 10),
            DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(R.plurals.download_amount, 25, 25),
            DownloadAction.UNREAD_CHAPTERS to stringResource(R.string.download_unread),
        ).map { (downloadAction, string) ->
            DropdownMenuItem(
                text = { Text(text = string) },
                onClick = {
                    onDownloadClicked(downloadAction)
                    onDismissRequest()
                },
            )
        }
    }
}
