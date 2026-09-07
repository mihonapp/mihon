package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.MangaCover
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

data class SourceWithMangaCount(
    val sourceId: Long,
    val sourceName: String,
    val mangaCount: Int,
)

@Composable
fun MigrateSourceScreen(
    sourcesWithCounts: List<SourceWithMangaCount>,
    selectedSource: SourceWithMangaCount?,
    mangasForSelectedSource: List<LibraryManga>,
    availableTargetSources: List<SourceDescriptor>,
    onSelectSource: (SourceWithMangaCount) -> Unit,
    onBackToSourceList: () -> Unit,
    onSearchTargetSource: suspend (sourceId: Long, query: String) -> List<SManga>,
    onPerformMigration: (oldManga: LibraryManga, targetSource: SourceDescriptor, targetManga: SManga) -> Unit,
) {
    val strings = LocalStrings.current
    var activeMigrateManga by remember { mutableStateOf<LibraryManga?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("migrate-source-screen"),
    ) {
        if (selectedSource == null) {
            // Level 1: List sources that have manga in library
            Text(
                text = strings.migrateSelectSourceHeader,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (sourcesWithCounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.migrateNoMangaInLibrary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("migration-sources-list")) {
                    items(sourcesWithCounts, key = { it.sourceId }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSource(item) }
                                .padding(16.dp)
                                .testTag("migrate-source-item-${item.sourceId}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = item.sourceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = strings.migrateMangaCount(item.mangaCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            OutlinedButton(onClick = { onSelectSource(item) }) {
                                Text(strings.migrateViewManga)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else {
            // Level 2: List manga belonging to this source
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBackToSourceList, modifier = Modifier.testTag("migrate-back-btn")) {
                    Text(strings.migrateBackToSources)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = selectedSource.sourceName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.migrateSelectMangaHeader,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().testTag("migration-manga-list")) {
                items(mangasForSelectedSource, key = { it.id }) { manga ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeMigrateManga = manga }
                            .padding(12.dp)
                            .testTag("migrate-manga-item-${manga.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            MangaCover(
                                thumbnailUrl = manga.thumbnailUrl,
                                contentDescription = manga.title,
                                modifier = Modifier.width(48.dp).height(68.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = manga.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = manga.author ?: strings.mangaDetailStatusUnknown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Button(
                            onClick = { activeMigrateManga = manga },
                            modifier = Modifier.testTag("migrate-action-btn-${manga.id}"),
                        ) {
                            Text(strings.migrateAction)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // Migration Dialog
    activeMigrateManga?.let { mangaToMigrate ->
        MigrateMangaDialog(
            manga = mangaToMigrate,
            availableTargetSources = availableTargetSources.filter { it.id != mangaToMigrate.sourceId },
            onDismiss = { activeMigrateManga = null },
            onSearchTargetSource = onSearchTargetSource,
            onConfirmMigration = { targetSource, targetManga ->
                onPerformMigration(mangaToMigrate, targetSource, targetManga)
                activeMigrateManga = null
            },
        )
    }
}

@Composable
private fun MigrateMangaDialog(
    manga: LibraryManga,
    availableTargetSources: List<SourceDescriptor>,
    onDismiss: () -> Unit,
    onSearchTargetSource: suspend (sourceId: Long, query: String) -> List<SManga>,
    onConfirmMigration: (targetSource: SourceDescriptor, targetManga: SManga) -> Unit,
) {
    val strings = LocalStrings.current
    var selectedTargetSource by remember {
        mutableStateOf(availableTargetSources.firstOrNull())
    }
    var query by remember { mutableStateOf(manga.title) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SManga>>(emptyList()) }
    var selectedCandidate by remember { mutableStateOf<SManga?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.migrateDialogTitle(manga.title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                Text(
                    text = strings.migrateDialogSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Source selector
                if (availableTargetSources.isEmpty()) {
                    Text(strings.migrateNoOtherSources, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.migrateTargetSourceLabel, style = MaterialTheme.typography.bodyMedium)
                        availableTargetSources.forEach { source ->
                            OutlinedButton(
                                onClick = {
                                    selectedTargetSource = source
                                    selectedCandidate = null
                                },
                                enabled = selectedTargetSource?.id != source.id,
                            ) {
                                Text(source.name)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search query and trigger
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).testTag("migrate-query-input"),
                        singleLine = true,
                        placeholder = { Text(strings.migrateSearchPlaceholder) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val target = selectedTargetSource ?: return@Button
                            isSearching = true
                            selectedCandidate = null
                            scope.launch {
                                try {
                                    searchResults = onSearchTargetSource(target.id, query)
                                } catch (_: Exception) {
                                    searchResults = emptyList()
                                } finally {
                                    isSearching = false
                                }
                            }
                        },
                        enabled = !isSearching && selectedTargetSource != null,
                        modifier = Modifier.testTag("migrate-search-submit"),
                    ) {
                        Text(strings.browseSearchButton)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searchResults) { candidate ->
                            val isSelected = selectedCandidate?.url == candidate.url
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCandidate = candidate }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    MangaCover(
                                        thumbnailUrl = candidate.thumbnailUrl,
                                        contentDescription = candidate.title,
                                        modifier = Modifier.width(36.dp).height(50.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                                if (isSelected) {
                                    Text(strings.migrateSelectedBadge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetSource = selectedTargetSource ?: return@Button
                    val candidate = selectedCandidate ?: return@Button
                    onConfirmMigration(targetSource, candidate)
                },
                enabled = selectedTargetSource != null && selectedCandidate != null,
                modifier = Modifier.testTag("confirm-migration-btn"),
            ) {
                Text(strings.migrateConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogCancel)
            }
        },
    )
}
