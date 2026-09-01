package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.library.model.LibraryManga

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onMangaSelected: (Long) -> Unit,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("library-screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
            )
            FilledTonalButton(
                onClick = onImportBackup,
                modifier = Modifier.testTag("library-import-backup"),
            ) {
                Text("Import Android backup")
            }
            FilledTonalButton(
                onClick = onImportLocal,
                modifier = Modifier.testTag("library-import-local"),
            ) {
                Text("Import local manga")
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("library-search"),
            label = { Text("Search title or author") },
            singleLine = true,
        )
        LibraryContent(
            state = state,
            onMangaSelected = onMangaSelected,
            onRetry = onRetry,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onMangaSelected: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when {
        state.loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag("library-loading"))
        }
        state.errorMessage != null -> ErrorState(state.errorMessage, onRetry, modifier)
        state.items.isEmpty() -> EmptyState(state.query, modifier)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = LibraryManga::id) { manga ->
                MangaCard(manga, onMangaSelected)
            }
        }
    }
}

@Composable
private fun MangaCard(
    manga: LibraryManga,
    onMangaSelected: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button) { onMangaSelected(manga.id) }
            .focusable(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Source ${manga.sourceId} · ${manga.chapterCount} ${chapterLabel(manga.chapterCount)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(
                modifier = Modifier.width(48.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${manga.unreadCount} unread",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(query: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isBlank()) {
                Text("Your library is empty", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Import an Android backup or local manga to start your collection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("No manga match “$query”", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Try a different title or author.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.testTag("library-error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.testTag("library-retry"),
            ) {
                Text("Retry")
            }
        }
    }
}

private fun chapterLabel(count: Long): String = if (count == 1L) "chapter" else "chapters"
