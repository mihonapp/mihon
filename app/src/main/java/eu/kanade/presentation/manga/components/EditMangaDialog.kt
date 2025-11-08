package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EditMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (title: String, author: String, artist: String, description: String) -> Unit,
    manga: Manga,
) {
    var title by remember(manga.title) { mutableStateOf(manga.title) }
    var author by remember(manga.author) { mutableStateOf(manga.author ?: "") }
    var artist by remember(manga.artist) { mutableStateOf(manga.artist ?: "") }
    var description by remember(manga.description) { mutableStateOf(manga.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title, author, artist, description)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_edit_info))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(MR.strings.title)) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(text = stringResource(MR.strings.author)) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(text = stringResource(MR.strings.artist)) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(text = stringResource(MR.strings.description_placeholder)) },
                )
            }
        },
    )
}