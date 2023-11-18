package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import eu.kanade.presentation.manga.DownloadAction
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.localize
import tachiyomi.presentation.core.i18n.localizePlural

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
            DownloadAction.NEXT_1_CHAPTER to localizePlural(MR.plurals.download_amount, 1, 1),
            DownloadAction.NEXT_5_CHAPTERS to localizePlural(MR.plurals.download_amount, 5, 5),
            DownloadAction.NEXT_10_CHAPTERS to localizePlural(MR.plurals.download_amount, 10, 10),
            DownloadAction.NEXT_25_CHAPTERS to localizePlural(MR.plurals.download_amount, 25, 25),
            DownloadAction.UNREAD_CHAPTERS to localize(MR.strings.download_unread),
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
