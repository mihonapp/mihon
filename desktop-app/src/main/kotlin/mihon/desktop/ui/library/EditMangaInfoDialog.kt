package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.MangaDetails

private val STATUS_OPTIONS = listOf(0L, 1L, 2L, 3L, 4L, 5L, 6L)

@Composable
fun EditMangaInfoDialog(
    manga: MangaDetails,
    onDismissRequest: () -> Unit,
    onSave: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit,
    onResetToSource: () -> Unit,
) {
    val strings = LocalStrings.current
    val initialGenres = remember(manga.genreJson) {
        try {
            Json.parseToJsonElement(manga.genreJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyList()
        }
    }

    var title by remember(manga.title) { mutableStateOf(manga.title) }
    var author by remember(manga.author) { mutableStateOf(manga.author ?: "") }
    var artist by remember(manga.artist) { mutableStateOf(manga.artist ?: "") }
    var description by remember(manga.description) { mutableStateOf(manga.description ?: "") }
    var genresText by remember(initialGenres) { mutableStateOf(initialGenres.joinToString(", ")) }
    var selectedStatus by remember(manga.status) { mutableStateOf(manga.status) }
    var notes by remember(manga.notes) { mutableStateOf(manga.notes) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(540.dp)
                .height(680.dp)
                .testTag("edit-manga-info-dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.mangaDetailEditInfo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = onResetToSource,
                        modifier = Modifier.testTag("edit-manga-reset-source-btn"),
                    ) {
                        Text(strings.mangaDetailResetToSource)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable fields
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(strings.mangaDetailTitleLabel) },
                        modifier = Modifier.fillMaxWidth().testTag("edit-manga-title-input"),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text(strings.mangaDetailAuthor) },
                            modifier = Modifier.weight(1f).testTag("edit-manga-author-input"),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = { Text(strings.mangaDetailArtist) },
                            modifier = Modifier.weight(1f).testTag("edit-manga-artist-input"),
                            singleLine = true,
                        )
                    }

                    // Status Dropdown
                    val currentStatusLabel = strings.mangaDetailStatusOption(selectedStatus)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.mangaDetailStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box {
                            OutlinedButton(
                                onClick = { statusDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag("edit-manga-status-select"),
                            ) {
                                Text(currentStatusLabel, modifier = Modifier.weight(1f))
                                Text("▼", style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(
                                expanded = statusDropdownExpanded,
                                onDismissRequest = { statusDropdownExpanded = false },
                            ) {
                                STATUS_OPTIONS.forEach { code ->
                                    DropdownMenuItem(
                                        text = { Text(strings.mangaDetailStatusOption(code)) },
                                        onClick = {
                                            selectedStatus = code
                                            statusDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = genresText,
                        onValueChange = { genresText = it },
                        label = { Text(strings.mangaDetailGenresLabel) },
                        modifier = Modifier.fillMaxWidth().testTag("edit-manga-genres-input"),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(strings.mangaDetailDescriptionLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("edit-manga-desc-input"),
                        maxLines = 4,
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(strings.mangaDetailNotes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("edit-manga-notes-input"),
                        maxLines = 3,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("edit-manga-cancel-btn"),
                    ) {
                        Text(strings.dialogCancel)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val parsedGenres = genresText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(
                                title.trim(),
                                author.trim().takeIf { it.isNotEmpty() },
                                artist.trim().takeIf { it.isNotEmpty() },
                                description.trim().takeIf { it.isNotEmpty() },
                                parsedGenres,
                                selectedStatus,
                                notes.trim(),
                            )
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.testTag("edit-manga-save-btn"),
                    ) {
                        Text(strings.mangaDetailSaveSuccess)
                    }
                }
            }
        }
    }
}
