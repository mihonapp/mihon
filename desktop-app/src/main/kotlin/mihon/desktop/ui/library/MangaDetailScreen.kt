package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onEditInfo: () -> Unit = {},
    onDismissEditInfo: () -> Unit = {},
    onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onResetMangaInfo: () -> Unit = {},
    onChapterFilterChange: (ChapterFilterState) -> Unit = {},
    onChapterSortChange: (ChapterSortState) -> Unit = {},
    onToggleBookmark: (Long) -> Unit = {},
    onToggleRead: (Long) -> Unit = {},
    onMarkPreviousRead: (Long) -> Unit = {},
    onDownloadChapter: (Long) -> Unit = {},
    onDeleteDownload: (Long) -> Unit = {},
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
                val strings = mihon.desktop.i18n.LocalStrings.current
                val customCoverManager = mihon.desktop.image.LocalCustomCoverManager.current
                val imageLoader = mihon.desktop.image.LocalImageLoader.current
                var coverRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
                val hasCustomCover = androidx.compose.runtime.remember(manga.id, coverRefreshKey) {
                    customCoverManager?.hasCustomCover(manga.id) == true
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "detail-header") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showBack) {
                                    TextButton(
                                        onClick = onBack,
                                        modifier = Modifier.testTag("manga-detail-back"),
                                    ) { Text(strings.mangaDetailBack) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = onEditInfo,
                                        modifier = Modifier.testTag("manga-detail-edit-info-button"),
                                    ) {
                                        Text(strings.mangaDetailEditInfo)
                                    }
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
                                    if (manga.url.startsWith("http")) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                mihon.desktop.platform.DesktopBrowserHelper.openInBrowser(manga.url)
                                            },
                                            modifier = Modifier.testTag("manga-detail-open-browser-button"),
                                        ) {
                                            Text(strings.openInBrowser)
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    androidx.compose.runtime.key(coverRefreshKey) {
                                        mihon.desktop.ui.common.MangaCover(
                                            thumbnailUrl = manga.thumbnailUrl,
                                            mangaId = manga.id,
                                            contentDescription = manga.title,
                                            modifier = Modifier.width(140.dp).height(200.dp),
                                            shape = MaterialTheme.shapes.medium,
                                        )
                                    }
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = {
                                            val picked = chooseCoverImage()
                                            if (picked != null) {
                                                customCoverManager?.setCustomCover(manga.id, picked)
                                                imageLoader?.clearMemoryCache()
                                                coverRefreshKey++
                                            }
                                        },
                                        modifier = Modifier.testTag("manga-detail-change-cover-btn"),
                                    ) {
                                        Text(strings.mangaDetailChangeCover)
                                    }
                                    if (hasCustomCover) {
                                        TextButton(
                                            onClick = {
                                                customCoverManager?.removeCustomCover(manga.id)
                                                imageLoader?.clearMemoryCache()
                                                coverRefreshKey++
                                            },
                                            modifier = Modifier.testTag("manga-detail-reset-cover-btn"),
                                        ) {
                                            Text(strings.mangaDetailResetCover)
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        manga.title,
                                        modifier = Modifier.testTag("manga-detail-title"),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    manga.author?.takeIf(String::isNotBlank)?.let {
                                        MetadataLine(strings.mangaDetailAuthor, it)
                                    }
                                    manga.artist?.takeIf(String::isNotBlank)?.let {
                                        MetadataLine(strings.mangaDetailArtist, it)
                                    }
                                    MetadataLine(strings.mangaDetailStatus, mangaStatusLabel(manga.status, strings))
                                    manga.description?.takeIf(String::isNotBlank)?.let {
                                        MetadataLine("Description", it)
                                    }
                                    val genres = decodeGenres(manga.genreJson)
                                    if (genres.isNotEmpty()) {
                                        MetadataLine(
                                            strings.mangaDetailGenres,
                                            genres.joinToString(" · "),
                                        )
                                    }
                                    if (manga.categories.isNotEmpty()) {
                                        MetadataLine("Categories", manga.categories.joinToString(" · ") { it.name })
                                    }
                                    manga.notes.takeIf(String::isNotBlank)?.let {
                                        MetadataLine(strings.mangaDetailNotes, it)
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val countText = if (state.chapters.size == state.allChapters.size) {
                                        "${strings.chapters} (${state.chapters.size})"
                                    } else {
                                        "${strings.chapters} (${state.chapters.size}/${state.allChapters.size})"
                                    }
                                    Text(countText, style = MaterialTheme.typography.titleLarge)

                                    var sortMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { sortMenuExpanded = true },
                                            modifier = Modifier.testTag("manga-detail-sort-button"),
                                        ) {
                                            val sortLabel = when (state.chapterSortState.mode) {
                                                ChapterSortMode.SourceOrder -> strings.sortSourceOrder
                                                ChapterSortMode.ChapterNumber -> strings.sortChapterNumber
                                                ChapterSortMode.UploadDate -> strings.sortUploadDate
                                            }
                                            val dirArrow = if (state.chapterSortState.ascending) "▲" else "▼"
                                            Text("$sortLabel $dirArrow")
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false },
                                        ) {
                                            ChapterSortMode.entries.forEach { mode ->
                                                val label = when (mode) {
                                                    ChapterSortMode.SourceOrder -> strings.sortSourceOrder
                                                    ChapterSortMode.ChapterNumber -> strings.sortChapterNumber
                                                    ChapterSortMode.UploadDate -> strings.sortUploadDate
                                                }
                                                val isCurrentMode = state.chapterSortState.mode == mode
                                                DropdownMenuItem(
                                                    text = {
                                                        val mark = if (isCurrentMode) "✓ " else ""
                                                        Text(mark + label)
                                                    },
                                                    onClick = {
                                                        if (isCurrentMode) {
                                                            onChapterSortChange(
                                                                state.chapterSortState.copy(
                                                                    ascending = !state.chapterSortState.ascending,
                                                                ),
                                                            )
                                                        } else {
                                                            onChapterSortChange(
                                                                state.chapterSortState.copy(mode = mode),
                                                            )
                                                        }
                                                        sortMenuExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = state.chapterFilterState.unread != TriStateFilter.Disabled,
                                        onClick = {
                                            onChapterFilterChange(
                                                state.chapterFilterState.copy(
                                                    unread = state.chapterFilterState.unread.next(),
                                                ),
                                            )
                                        },
                                        label = {
                                            val text = when (state.chapterFilterState.unread) {
                                                TriStateFilter.Include -> strings.filterUnreadOnly
                                                TriStateFilter.Exclude -> strings.filterReadOnly
                                                TriStateFilter.Disabled -> strings.filterUnread
                                            }
                                            Text(text)
                                        },
                                        modifier = Modifier.testTag("chapter-filter-unread"),
                                    )
                                    FilterChip(
                                        selected = state.chapterFilterState.downloaded != TriStateFilter.Disabled,
                                        onClick = {
                                            onChapterFilterChange(
                                                state.chapterFilterState.copy(
                                                    downloaded = state.chapterFilterState.downloaded.next(),
                                                ),
                                            )
                                        },
                                        label = {
                                            val text = when (state.chapterFilterState.downloaded) {
                                                TriStateFilter.Include -> strings.filterDownloadedOnly
                                                TriStateFilter.Exclude -> strings.filterNotDownloadedOnly
                                                TriStateFilter.Disabled -> strings.filterDownloaded
                                            }
                                            Text(text)
                                        },
                                        modifier = Modifier.testTag("chapter-filter-downloaded"),
                                    )
                                    FilterChip(
                                        selected = state.chapterFilterState.bookmarked != TriStateFilter.Disabled,
                                        onClick = {
                                            onChapterFilterChange(
                                                state.chapterFilterState.copy(
                                                    bookmarked = state.chapterFilterState.bookmarked.next(),
                                                ),
                                            )
                                        },
                                        label = {
                                            val text = when (state.chapterFilterState.bookmarked) {
                                                TriStateFilter.Include -> strings.filterBookmarkedOnly
                                                TriStateFilter.Exclude -> strings.filterNotBookmarkedOnly
                                                TriStateFilter.Disabled -> strings.filterBookmarked
                                            }
                                            Text(text)
                                        },
                                        modifier = Modifier.testTag("chapter-filter-bookmarked"),
                                    )
                                }
                            }
                        }
                    }
                    items(state.chapters, key = LibraryChapter::id) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            availability = state.readerAvailability[chapter.id] ?: ChapterReaderAvailability.RemoteOnly,
                            isDownloaded = state.downloadedChapterIds.contains(chapter.id),
                            onReadChapter = onReadChapter,
                            onToggleBookmark = { onToggleBookmark(chapter.id) },
                            onToggleRead = { onToggleRead(chapter.id) },
                            onMarkPreviousRead = { onMarkPreviousRead(chapter.id) },
                            onDownloadChapter = { onDownloadChapter(chapter.id) },
                            onDeleteDownload = { onDeleteDownload(chapter.id) },
                        )
                    }
                    item(key = "detail-bottom-space") {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    }
                }

                if (state.isEditInfoDialogOpen) {
                    EditMangaInfoDialog(
                        manga = manga,
                        onDismissRequest = onDismissEditInfo,
                        onSave = onSaveMangaInfo,
                        onResetToSource = onResetMangaInfo,
                    )
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

private fun mangaStatusLabel(status: Long, strings: mihon.desktop.i18n.DesktopStrings): String = when (status) {
    1L -> strings.mangaDetailStatusOngoing
    2L -> strings.mangaDetailStatusCompleted
    else -> strings.mangaDetailStatusUnknown
}

@Composable
private fun ChapterRow(
    chapter: LibraryChapter,
    availability: ChapterReaderAvailability,
    isDownloaded: Boolean,
    onReadChapter: (Long) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    onMarkPreviousRead: () -> Unit,
    onDownloadChapter: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chapter.bookmark) {
                        Text("★", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(chapter.name, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    chapterProgressLabel(chapter, strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.testTag("chapter-bookmark-button"),
            ) {
                val color = if (chapter.bookmark) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    if (chapter.bookmark) "★" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }

            IconButton(
                onClick = {
                    if (isDownloaded) onDeleteDownload() else onDownloadChapter()
                },
                modifier = Modifier.testTag("chapter-download-button"),
            ) {
                val color = if (isDownloaded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    if (isDownloaded) "✓" else "↓",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("chapter-more-button"),
                ) {
                    Text("⋮", style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (chapter.read) strings.markAsUnread else strings.markAsRead) },
                        onClick = {
                            onToggleRead()
                            menuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.markPreviousAsRead) },
                        onClick = {
                            onMarkPreviousRead()
                            menuExpanded = false
                        },
                    )
                }
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
    val strings = mihon.desktop.i18n.LocalStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyLarge)
        onRetry?.let {
            FilledTonalButton(onClick = it, modifier = Modifier.testTag("manga-detail-retry")) {
                Text(strings.libraryRetry)
            }
        }
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("manga-detail-back")) {
                Text(strings.mangaDetailBackToLibrary)
            }
        }
    }
}

private fun chapterProgressLabel(chapter: LibraryChapter, strings: mihon.desktop.i18n.DesktopStrings): String = buildList {
    add(if (chapter.read) strings.filterRead else strings.filterUnread)
    if (chapter.bookmark) add(strings.filterBookmarked)
    if (chapter.lastPageRead > 0) add(strings.browsePageNumber(chapter.lastPageRead.toInt()))
}.joinToString(" · ")

private fun decodeGenres(raw: String): List<String> = try {
    Json.decodeFromString<List<String>>(raw)
} catch (_: Exception) {
    emptyList()
}

internal fun chooseCoverImage(): java.nio.file.Path? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose Cover Image", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val l = name.lowercase()
        l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
    }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return java.nio.file.Path.of(dir, file)
}
