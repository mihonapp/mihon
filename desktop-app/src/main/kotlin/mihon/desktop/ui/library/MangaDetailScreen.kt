package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import mihon.desktop.library.model.LibraryChapter

@Composable
fun MangaDetailScreen(
    state: MangaDetailUiState,
    onBack: () -> Unit,
    onReadChapter: (Long) -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onEditCategories: () -> Unit = {},
    onOpenTracking: () -> Unit = {},
) {
    Surface(
        modifier = modifier.testTag("manga-detail-pane"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when {
            state.loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("manga-detail-loading"))
            }
            state.errorMessage != null -> DetailMessage(
                message = state.errorMessage,
                tag = "manga-detail-error",
                onBack = onBack,
                showBack = showBack,
                onRetry = onRetry,
            )
            state.manga == null -> DetailMessage(
                message = "This manga is no longer in your library.",
                tag = "manga-detail-missing",
                onBack = onBack,
                showBack = showBack,
                onRetry = null,
            )
            else -> {
                val manga = state.manga
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "detail-header") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val strings = mihon.desktop.i18n.LocalStrings.current
                                if (showBack) {
                                    TextButton(
                                        onClick = onBack,
                                        modifier = Modifier.testTag("manga-detail-back"),
                                    ) { Text(strings.mangaDetailBack) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = onEditCategories,
                                        modifier = Modifier.testTag("manga-detail-edit-categories-button"),
                                    ) {
                                        Text(strings.mangaDetailCategories)
                                    }
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = onOpenTracking,
                                        modifier = Modifier.testTag("manga-detail-open-tracking-button"),
                                    ) {
                                        Text(strings.mangaDetailTracking)
                                    }
                                }
                            }
                            Text(
                                manga.title,
                                modifier = Modifier.testTag("manga-detail-title"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            manga.author?.takeIf(String::isNotBlank)?.let { MetadataLine("Author", it) }
                            manga.description?.takeIf(String::isNotBlank)?.let { MetadataLine("Description", it) }
                            val genres = decodeGenres(manga.genreJson)
                            if (genres.isNotEmpty()) MetadataLine("Genres", genres.joinToString(" · "))
                            if (manga.categories.isNotEmpty()) {
                                MetadataLine("Categories", manga.categories.joinToString(" · ") { it.name })
                            }
                            manga.notes.takeIf(String::isNotBlank)?.let { MetadataLine("Notes", it) }
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            Text("Chapters", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    items(state.chapters, key = LibraryChapter::id) { chapter ->
                        ChapterRow(
                            chapter,
                            state.readerAvailability[chapter.id] ?: ChapterReaderAvailability.RemoteOnly,
                            onReadChapter,
                        )
                    }
                    item(key = "detail-bottom-space") {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChapterRow(
    chapter: LibraryChapter,
    availability: ChapterReaderAvailability,
    onReadChapter: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("chapter-row")
            .semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(chapter.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    chapterProgressLabel(chapter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { onReadChapter(chapter.id) },
                enabled = availability is ChapterReaderAvailability.Readable,
                modifier = Modifier.testTag("chapter-reader-action"),
            ) {
                Text(chapterActionLabel(chapter, availability))
            }
        }
    }
}

private fun chapterActionLabel(
    chapter: LibraryChapter,
    availability: ChapterReaderAvailability,
): String = when (availability) {
    ChapterReaderAvailability.Readable -> if (chapter.lastPageRead > 0L) {
        "Continue · Page ${chapter.lastPageRead}"
    } else {
        "Read"
    }
    ChapterReaderAvailability.MissingLocalContent -> "Locate or re-import local content"
    ChapterReaderAvailability.RemoteOnly -> "Available after source support"
}

@Composable
private fun DetailMessage(
    message: String,
    tag: String,
    onBack: () -> Unit,
    showBack: Boolean,
    onRetry: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyLarge)
        onRetry?.let {
            FilledTonalButton(onClick = it, modifier = Modifier.testTag("manga-detail-retry")) {
                Text("Retry")
            }
        }
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("manga-detail-back")) {
                Text("Back to Library")
            }
        }
    }
}

private fun chapterProgressLabel(chapter: LibraryChapter): String = buildList {
    add(if (chapter.read) "Read" else "Unread")
    if (chapter.bookmark) add("Bookmarked")
    if (chapter.lastPageRead > 0) add("Page ${chapter.lastPageRead}")
}.joinToString(" · ")

private fun decodeGenres(raw: String): List<String> = try {
    Json.decodeFromString<List<String>>(raw)
} catch (_: Exception) {
    emptyList()
}
