package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.translation.model.TranslationResult
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Data class to hold all translation results
 */
data class TranslatedMangaDetails(
    val translatedTitle: String? = null,
    val translatedDescription: String? = null,
    val translatedGenres: List<String>? = null,
    val addToAltTitles: Boolean = true,
    val saveTagsToNotes: Boolean = false,
)

@Composable
fun TranslateMangaDetailsDialog(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onConfirm: (details: TranslatedMangaDetails) -> Unit,
) {
    val translationEngineManager: TranslationEngineManager = remember { Injekt.get() }

    var isTranslating by remember { mutableStateOf(false) }
    var translatedTitle by remember { mutableStateOf<String?>(null) }
    var translatedDescription by remember { mutableStateOf<String?>(null) }
    var translatedGenres by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addToAltTitles by remember { mutableStateOf(true) }
    var saveTagsToNotes by remember { mutableStateOf(false) }
    var translateGenres by remember { mutableStateOf(true) }

    // Start translation on dialog open
    LaunchedEffect(Unit) {
        isTranslating = true
        error = null

        try {
            val engine = translationEngineManager.getSelectedEngine()
            if (engine == null) {
                error = "No translation engine configured. Please configure one in Settings > Translation."
                isTranslating = false
                return@LaunchedEffect
            }

            // Translate title
            val titleResult = engine.translateSingle(manga.title, sourceLanguage = "auto", targetLanguage = "en")
            when (titleResult) {
                is TranslationResult.Success -> {
                    translatedTitle = titleResult.translatedTexts.firstOrNull()
                }
                is TranslationResult.Error -> {
                    error = "Failed to translate title: ${titleResult.message}"
                }
            }

            // Translate description if present
            manga.description?.let { desc ->
                if (desc.isNotBlank()) {
                    val descResult = engine.translateSingle(desc, sourceLanguage = "auto", targetLanguage = "en")
                    when (descResult) {
                        is TranslationResult.Success -> {
                            translatedDescription = descResult.translatedTexts.firstOrNull()
                        }
                        is TranslationResult.Error -> {
                            // Don't fail the whole dialog if description fails
                            translatedDescription = null
                        }
                    }
                }
            }

            // Translate genres if present
            val genres = manga.genre
            if (!genres.isNullOrEmpty()) {
                val genresResult = engine.translate(genres, sourceLanguage = "auto", targetLanguage = "en")
                when (genresResult) {
                    is TranslationResult.Success -> {
                        translatedGenres = genresResult.translatedTexts
                    }
                    is TranslationResult.Error -> {
                        // Don't fail if genre translation fails
                        translatedGenres = null
                    }
                }
            }
        } catch (e: Exception) {
            error = "Translation failed: ${e.message}"
        } finally {
            isTranslating = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Translate Novel Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isTranslating) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text("Translating...")
                    }
                } else if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    // Original title
                    Text(
                        text = "Original Title:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // Translated title
                    if (translatedTitle != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Translated Title:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = translatedTitle!!,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // Checkbox to add to alt titles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = addToAltTitles,
                                onCheckedChange = { addToAltTitles = it },
                            )
                            Text(
                                text = "Add translated title to alternative titles",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        // Checkbox to save tags to notes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = saveTagsToNotes,
                                onCheckedChange = { saveTagsToNotes = it },
                            )
                            Text(
                                text = "Save translated tags to notes",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // Original description
                    if (!manga.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Original Description:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = manga.description!!.take(200) + if (manga.description!!.length > 200) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        // Translated description
                        if (translatedDescription != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Translated Description:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text =
                                translatedDescription!!.take(200) +
                                    if (translatedDescription!!.length > 200) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // Original genres
                    if (!manga.genre.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Original Genres:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text =
                            manga.genre!!.take(10).joinToString(", ") + if (manga.genre!!.size > 10) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        // Translated genres
                        if (translatedGenres != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Translated Genres:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text =
                                translatedGenres!!.take(10).joinToString(", ") +
                                    if (translatedGenres!!.size > 10) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                            )

                            // Checkbox to save translated genres
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = translateGenres,
                                    onCheckedChange = { translateGenres = it },
                                )
                                Text(
                                    text = "Save translated genres",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        TranslatedMangaDetails(
                            translatedTitle = translatedTitle,
                            translatedDescription = translatedDescription,
                            translatedGenres = if (translateGenres) translatedGenres else null,
                            addToAltTitles = addToAltTitles,
                            saveTagsToNotes = saveTagsToNotes,
                        ),
                    )
                },
                enabled = !isTranslating && translatedTitle != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}
