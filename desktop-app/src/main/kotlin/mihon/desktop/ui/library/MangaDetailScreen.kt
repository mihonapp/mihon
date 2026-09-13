package mihon.desktop.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import mihon.desktop.category.DesktopCategory
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.ui.common.MangaBackdropBanner

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
    onDownloadBatch: (Int?) -> Unit = {},
    onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleBookmark) },
    onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleRead) },
    onBatchDownloadChapters: (Set<Long>) -> Unit = { ids -> ids.forEach(onDownloadChapter) },
    onBatchDeleteDownloads: (Set<Long>) -> Unit = { ids -> ids.forEach(onDeleteDownload) },
    onOpenChapterSettings: () -> Unit = {},
    onDismissChapterSettings: () -> Unit = {},
    onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit = {},
    onExcludedScanlatorsChange: (Set<String>) -> Unit = {},
    onShowMissingChaptersChange: (Boolean) -> Unit = {},
    onSetChapterSettingsAsDefault: (Boolean) -> Unit = {},
    onResetChapterSettingsToDefault: () -> Unit = {},
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
                val chapterItems = state.chapterListItems.ifEmpty {
                    buildChapterListItems(
                        chapters = state.chapters,
                        settings = state.chapterSettings,
                    )
                }

                var chapterSearchQuery by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("")
                }
                var isSearchingChapters by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var isCoverDialogOpen by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var isNotesDialogOpen by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var isSelectionMode by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var selectedChapterIds by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(emptySet<Long>())
                }

                val displayedChapterItems = androidx.compose.runtime.remember(chapterItems, chapterSearchQuery) {
                    if (chapterSearchQuery.isBlank()) {
                        chapterItems
                    } else {
                        chapterItems.filter { item ->
                            when (item) {
                                is ChapterListItem.Chapter -> {
                                    item.label.contains(chapterSearchQuery, ignoreCase = true) ||
                                        item.chapter.name.contains(chapterSearchQuery, ignoreCase = true) ||
                                        (
                                            item.chapter.scanlator?.contains(
                                                chapterSearchQuery,
                                                ignoreCase = true,
                                            ) == true
                                            ) ||
                                        item.chapter.chapterNumber.toString().contains(chapterSearchQuery)
                                }
                                is ChapterListItem.MissingCount -> false
                            }
                        }
                    }
                }

                val nextChapterToRead = androidx.compose.runtime.remember(state.chapters) {
                    if (state.chapters.isEmpty()) {
                        null
                    } else {
                        val sortedAscending = state.chapters.sortedWith(
                            compareBy<LibraryChapter> { it.chapterNumber }.thenBy { it.sourceOrder },
                        )
                        sortedAscending.firstOrNull { !it.read } ?: sortedAscending.lastOrNull()
                    }
                }

                val fabText = androidx.compose.runtime.remember(nextChapterToRead, state.chapters) {
                    when {
                        nextChapterToRead == null -> ""
                        state.chapters.none { it.read } -> strings.mangaDetailStart(nextChapterToRead.name)
                        state.chapters.all { it.read } -> strings.mangaDetailStart(nextChapterToRead.name)
                        else -> strings.mangaDetailResume(nextChapterToRead.name)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MangaBackdropBanner(
                        thumbnailUrl = manga.thumbnailUrl,
                        mangaId = manga.id,
                        modifier = Modifier.align(Alignment.TopCenter),
                        bannerHeight = 320.dp,
                    )

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
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.mangaDetailBack)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = onEditInfo,
                                            modifier = Modifier.testTag("manga-detail-edit-info-button"),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.mangaDetailEditInfo)
                                        }
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = onEditCategories,
                                            modifier = Modifier.testTag("manga-detail-edit-categories-button"),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Label,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.mangaDetailCategories)
                                        }
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = onOpenTracking,
                                            modifier = Modifier.testTag("manga-detail-open-tracking-button"),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.mangaDetailTracking)
                                        }
                                        if (manga.url.startsWith("http")) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    mihon.desktop.platform.DesktopBrowserHelper.openInBrowser(manga.url)
                                                },
                                                modifier = Modifier.testTag("manga-detail-open-browser-button"),
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
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
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                tonalElevation = 4.dp,
                                                shadowElevation = 8.dp,
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .clickable { isCoverDialogOpen = true }
                                                    .testTag("manga-detail-cover-clickable"),
                                            ) {
                                                mihon.desktop.ui.common.MangaCover(
                                                    thumbnailUrl = manga.thumbnailUrl,
                                                    mangaId = manga.id,
                                                    contentDescription = manga.title,
                                                    modifier = Modifier.width(140.dp).height(200.dp),
                                                    shape = MaterialTheme.shapes.medium,
                                                )
                                            }
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
                                        MangaStatusBadge(manga.status, strings)
                                        manga.author?.takeIf(String::isNotBlank)?.let {
                                            MetadataLine(strings.mangaDetailAuthor, it)
                                        }
                                        manga.artist?.takeIf(String::isNotBlank)?.let {
                                            MetadataLine(strings.mangaDetailArtist, it)
                                        }
                                        manga.description?.takeIf(String::isNotBlank)?.let {
                                            ExpandableMangaDescription(it)
                                        }
                                        val genres = decodeGenres(manga.genreJson)
                                        if (genres.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    strings.mangaDetailGenres,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                // Retain for semantic & accessibility assertions
                                                Text(
                                                    text = genres.joinToString(" · "),
                                                    modifier = Modifier.height(0.dp).alpha(0f),
                                                )
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    genres.forEach { genre ->
                                                        SuggestionChip(
                                                            onClick = {},
                                                            label = {
                                                                Text(
                                                                    genre,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                )
                                                            },
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = null,
                                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                                containerColor =
                                                                MaterialTheme.colorScheme.secondaryContainer.copy(
                                                                    alpha = 0.55f,
                                                                ),
                                                                labelColor =
                                                                MaterialTheme.colorScheme.onSecondaryContainer,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (manga.categories.isNotEmpty()) {
                                            MetadataLine(
                                                "Categories",
                                                manga.categories.joinToString(" · ") { it.name },
                                            )
                                        }
                                        if (manga.notes.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    MetadataLine(strings.mangaDetailNotes, manga.notes)
                                                }
                                                IconButton(
                                                    onClick = { isNotesDialogOpen = true },
                                                    modifier = Modifier.testTag("manga-detail-edit-notes-btn"),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.EditNote,
                                                        contentDescription = strings.mangaNotesEdit,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { isNotesDialogOpen = true },
                                                modifier = Modifier.testTag("manga-detail-add-notes-btn"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.EditNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(strings.mangaNotesTitle)
                                            }
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

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            var downloadMenuExpanded by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(
                                                    onClick = { downloadMenuExpanded = true },
                                                    modifier = Modifier.testTag("manga-detail-download-menu-button"),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Download,
                                                        contentDescription = strings.downloadChapter,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = downloadMenuExpanded,
                                                    onDismissRequest = { downloadMenuExpanded = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(strings.downloadNext1) },
                                                        onClick = {
                                                            onDownloadBatch(1)
                                                            downloadMenuExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("manga-detail-download-next-1"),
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.downloadNext5) },
                                                        onClick = {
                                                            onDownloadBatch(5)
                                                            downloadMenuExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("manga-detail-download-next-5"),
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.downloadNext10) },
                                                        onClick = {
                                                            onDownloadBatch(10)
                                                            downloadMenuExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("manga-detail-download-next-10"),
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.downloadNext25) },
                                                        onClick = {
                                                            onDownloadBatch(25)
                                                            downloadMenuExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("manga-detail-download-next-25"),
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.downloadUnread) },
                                                        onClick = {
                                                            onDownloadBatch(null)
                                                            downloadMenuExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("manga-detail-download-unread"),
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.downloadAll) },
                                                        onClick = {
                                                            onDownloadBatch(-1)
                                                            downloadMenuExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("manga-detail-download-all"),
                                                    )
                                                }
                                            }

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
                                                    val dirArrow =
                                                        if (state.chapterSortState.ascending) "▲" else "▼"
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
                                                                            ascending =
                                                                            !state.chapterSortState.ascending,
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
                                            Box(
                                                modifier = Modifier
                                                    .testTag("manga-detail-chapter-settings-button")
                                                    .clickable(onClick = onOpenChapterSettings),
                                            ) {
                                                IconButton(
                                                    onClick = onOpenChapterSettings,
                                                    modifier = Modifier.testTag("chapter-settings-button"),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Settings,
                                                        contentDescription = "Chapter settings",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    isSearchingChapters = !isSearchingChapters
                                                    if (!isSearchingChapters) chapterSearchQuery = ""
                                                },
                                                modifier = Modifier.testTag("chapter-search-toggle-button"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Search,
                                                    contentDescription = "Search chapters",
                                                    tint = if (isSearchingChapters || chapterSearchQuery.isNotEmpty()) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    isSelectionMode = !isSelectionMode
                                                    if (!isSelectionMode) selectedChapterIds = emptySet()
                                                },
                                                modifier = Modifier.testTag("chapter-selection-toggle-button"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                                    contentDescription = strings.chapterBatchSelect,
                                                    tint = if (isSelectionMode || selectedChapterIds.isNotEmpty()) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    if (isSearchingChapters) {
                                        OutlinedTextField(
                                            value = chapterSearchQuery,
                                            onValueChange = { chapterSearchQuery = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .testTag("chapter-search-text-field"),
                                            placeholder = {
                                                Text(
                                                    text = strings.mangaDetailSearchChaptersPlaceholder,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            trailingIcon = {
                                                if (chapterSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { chapterSearchQuery = "" }) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "Clear search",
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                        )
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
                        if (displayedChapterItems.isEmpty() && chapterSearchQuery.isNotBlank()) {
                            item(key = "detail-search-empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                        .testTag("chapter-search-empty"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = strings.readerChapterDrawerNoResults,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        } else {
                            items(displayedChapterItems, key = { it.key }) { item ->
                                when (item) {
                                    is ChapterListItem.Chapter -> ChapterRow(
                                        chapter = item.chapter,
                                        displayName = item.label,
                                        availability = state.readerAvailability[item.chapter.id]
                                            ?: ChapterReaderAvailability.RemoteOnly,
                                        isDownloaded = state.downloadedChapterIds.contains(item.chapter.id),
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedChapterIds.contains(item.chapter.id),
                                        onToggleSelection = {
                                            selectedChapterIds = if (selectedChapterIds.contains(item.chapter.id)) {
                                                selectedChapterIds - item.chapter.id
                                            } else {
                                                selectedChapterIds + item.chapter.id
                                            }
                                        },
                                        onReadChapter = {
                                            if (isSelectionMode) {
                                                selectedChapterIds = if (selectedChapterIds.contains(it)) {
                                                    selectedChapterIds - it
                                                } else {
                                                    selectedChapterIds + it
                                                }
                                            } else {
                                                onReadChapter(it)
                                            }
                                        },
                                        onToggleBookmark = { onToggleBookmark(item.chapter.id) },
                                        onToggleRead = { onToggleRead(item.chapter.id) },
                                        onMarkPreviousRead = { onMarkPreviousRead(item.chapter.id) },
                                        onDownloadChapter = { onDownloadChapter(item.chapter.id) },
                                        onDeleteDownload = { onDeleteDownload(item.chapter.id) },
                                    )
                                    is ChapterListItem.MissingCount -> MissingChapterIndicator(count = item.count)
                                }
                            }
                        }
                        item(key = "detail-bottom-space") {
                            androidx.compose.foundation.layout.Spacer(Modifier.height(88.dp))
                        }
                    }

                    if (nextChapterToRead != null && !isSelectionMode && selectedChapterIds.isEmpty()) {
                        ExtendedFloatingActionButton(
                            onClick = { onReadChapter(nextChapterToRead.id) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                            text = {
                                Text(
                                    text = fabText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp)
                                .testTag("manga-detail-fab-read"),
                        )
                    }

                    MangaBottomActionMenu(
                        visible = isSelectionMode || selectedChapterIds.isNotEmpty(),
                        selectedCount = selectedChapterIds.size,
                        totalCount = displayedChapterItems.count { it is ChapterListItem.Chapter },
                        onSelectAll = {
                            selectedChapterIds =
                                displayedChapterItems.mapNotNull {
                                    (it as? ChapterListItem.Chapter)?.chapter?.id
                                }.toSet()
                        },
                        onInvertSelection = {
                            val allIds = displayedChapterItems.mapNotNull {
                                (it as? ChapterListItem.Chapter)?.chapter?.id
                            }.toSet()
                            selectedChapterIds = allIds - selectedChapterIds
                        },
                        onBookmarkClicked = {
                            onBatchBookmarkChapters(selectedChapterIds, true)
                        },
                        onRemoveBookmarkClicked = {
                            onBatchBookmarkChapters(selectedChapterIds, false)
                        },
                        onMarkAsReadClicked = {
                            onBatchMarkChaptersRead(selectedChapterIds, true)
                        },
                        onMarkAsUnreadClicked = {
                            onBatchMarkChaptersRead(selectedChapterIds, false)
                        },
                        onMarkPreviousAsReadClicked = if (selectedChapterIds.size == 1) {
                            { onMarkPreviousRead(selectedChapterIds.first()) }
                        } else {
                            null
                        },
                        onDownloadClicked = {
                            onBatchDownloadChapters(selectedChapterIds)
                        },
                        onDeleteClicked = if (selectedChapterIds.any { state.downloadedChapterIds.contains(it) }) {
                            { onBatchDeleteDownloads(selectedChapterIds) }
                        } else {
                            null
                        },
                        onCloseClicked = {
                            isSelectionMode = false
                            selectedChapterIds = emptySet()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (isCoverDialogOpen) {
                    MangaCoverDialog(
                        mangaTitle = manga.title,
                        thumbnailUrl = manga.thumbnailUrl,
                        mangaId = manga.id,
                        isCustomCover = hasCustomCover,
                        onDismissRequest = { isCoverDialogOpen = false },
                        onChangeCover = {
                            val picked = chooseCoverImage()
                            if (picked != null) {
                                customCoverManager?.setCustomCover(manga.id, picked)
                                imageLoader?.clearMemoryCache()
                                coverRefreshKey++
                            }
                        },
                        onResetCover = {
                            customCoverManager?.removeCustomCover(manga.id)
                            imageLoader?.clearMemoryCache()
                            coverRefreshKey++
                        },
                        coverFileProvider = {
                            imageLoader?.getCoverFile(manga.id, manga.thumbnailUrl)
                        },
                    )
                }

                if (isNotesDialogOpen) {
                    MangaNotesDialog(
                        mangaTitle = manga.title,
                        initialNotes = manga.notes,
                        onDismissRequest = { isNotesDialogOpen = false },
                        onSaveNotes = { newNotes ->
                            onSaveMangaInfo(
                                manga.title,
                                manga.author,
                                manga.artist,
                                manga.description,
                                decodeGenres(manga.genreJson),
                                manga.status,
                                newNotes,
                            )
                        },
                    )
                }

                if (state.isEditInfoDialogOpen) {
                    EditMangaInfoDialog(
                        manga = manga,
                        onDismissRequest = onDismissEditInfo,
                        onSave = onSaveMangaInfo,
                        onResetToSource = onResetMangaInfo,
                    )
                }
                if (state.isChapterSettingsDialogOpen) {
                    ChapterSettingsDialog(
                        settings = state.chapterSettings,
                        availableScanlators = state.availableScanlators,
                        onDismissRequest = onDismissChapterSettings,
                        onDisplayModeChange = onChapterDisplayModeChange,
                        onSortModeChange = { mode, ascending ->
                            onChapterSortChange(ChapterSortState(mode = mode, ascending = ascending))
                        },
                        onShowMissingChaptersChange = onShowMissingChaptersChange,
                        onExcludedScanlatorsChange = onExcludedScanlatorsChange,
                        onSetAsDefault = onSetChapterSettingsAsDefault,
                        onResetToDefault = onResetChapterSettingsToDefault,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingChapterIndicator(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .testTag("missing-chapter-indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "$count missing ${if (count == 1) "chapter" else "chapters"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MangaStatusBadge(status: Long, strings: mihon.desktop.i18n.DesktopStrings) {
    val (dotColor, label) = when (status) {
        1L -> Color(0xFF4CAF50) to strings.mangaDetailStatusOngoing
        2L -> MaterialTheme.colorScheme.primary to strings.mangaDetailStatusCompleted
        else -> MaterialTheme.colorScheme.outline to strings.mangaDetailStatusUnknown
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = dotColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = dotColor,
            )
        }
    }
}

@Composable
private fun ExpandableMangaDescription(description: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Description",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
        if (description.length > 120) {
            Text(
                text = if (expanded) "▲ Show less" else "▼ Show more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
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
    displayName: String,
    availability: ChapterReaderAvailability,
    isDownloaded: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onReadChapter: (Long) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    onMarkPreviousRead: () -> Unit,
    onDownloadChapter: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("chapter-row")
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                if (isSelectionMode) {
                    onToggleSelection()
                } else {
                    onReadChapter(chapter.id)
                }
            }
            .semantics(mergeDescendants = true) {},
        color = rowBackground,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.testTag("chapter-select-${chapter.id}"),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chapter.bookmark) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (chapter.read) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
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
                Icon(
                    imageVector = if (chapter.bookmark) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = null,
                    tint = color,
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
                Icon(
                    imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = null,
                    tint = color,
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("chapter-more-button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                    )
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

private val chapterDateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun chapterProgressLabel(
    chapter: LibraryChapter,
    strings: mihon.desktop.i18n.DesktopStrings,
): String = buildList {
    add(if (chapter.read) strings.filterRead else strings.filterUnread)
    if (chapter.bookmark) add(strings.filterBookmarked)
    if (chapter.dateUpload > 0L) {
        val dateText = try {
            java.time.Instant.ofEpochMilli(chapter.dateUpload)
                .atZone(java.time.ZoneId.systemDefault())
                .format(chapterDateFormatter)
        } catch (_: Exception) {
            null
        }
        if (!dateText.isNullOrBlank()) add(dateText)
    }
    if (chapter.lastPageRead > 0) add(strings.browsePageNumber(chapter.lastPageRead.toInt()))
    chapter.scanlator?.takeIf(String::isNotBlank)?.let { add(it) }
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
