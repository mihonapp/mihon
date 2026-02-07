package eu.kanade.tachiyomi.ui.library.duplicate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.DuplicateMatchMode
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DuplicateDetectionScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val clipboardManager = LocalClipboardManager.current
        val snackbarHostState = remember { SnackbarHostState() }

        val screenModel = rememberScreenModel { DuplicateDetectionScreenModel() }
        val state by screenModel.state.collectAsState()
        
        // Preserve scroll position across navigation
        val listState = rememberLazyListState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Find Duplicates")
                            if (state.selection.isNotEmpty()) {
                                Text(
                                    "${state.selection.size} selected",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (state.selection.isNotEmpty()) {
                            // Copy links
                            IconButton(onClick = {
                                val urls = screenModel.getSelectedUrls()
                                clipboardManager.setText(AnnotatedString(urls.joinToString("\n")))
                                scope.launch {
                                    snackbarHostState.showSnackbar("${urls.size} URLs copied")
                                }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Links")
                            }
                            // Delete selected
                            IconButton(onClick = {
                                screenModel.openDeleteDialog()
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Selected")
                            }
                            // Move to category
                            IconButton(onClick = {
                                screenModel.openMoveToCategoryDialog()
                            }) {
                                Icon(Icons.Filled.DriveFileMove, contentDescription = "Move to Category")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (state.filteredDuplicateGroups.isNotEmpty()) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        FloatingActionButton(
                            onClick = { showMenu = true },
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Selection Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Select ALL Duplicates") },
                                onClick = {
                                    screenModel.selectAllDuplicates()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select All Except First") },
                                onClick = {
                                    screenModel.selectAllExceptFirst()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select With Lowest Ch Count") },
                                onClick = {
                                    screenModel.selectLowestChapterCount()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select With Highest Ch Count") },
                                onClick = {
                                    screenModel.selectHighestChapterCount()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select With Highest Downloads") },
                                onClick = {
                                    screenModel.selectHighestDownloadCount()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select With Lowest Downloads") },
                                onClick = {
                                    screenModel.selectLowestDownloadCount()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select With Highest Read Count") },
                                onClick = {
                                    screenModel.selectHighestReadCount()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select With Lowest Read Count") },
                                onClick = {
                                    screenModel.selectLowestReadCount()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select Pinned Sources") },
                                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                                onClick = {
                                    screenModel.selectPinnedInGroups()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select Non-Pinned Sources") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                onClick = {
                                    screenModel.selectNonPinnedInGroups()
                                    showMenu = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Clear Selection") },
                                onClick = {
                                    screenModel.clearSelection()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Invert Selection") },
                                onClick = {
                                    screenModel.invertSelection()
                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                // Match mode selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.matchMode == DuplicateMatchMode.EXACT,
                        onClick = { screenModel.setMatchMode(DuplicateMatchMode.EXACT) },
                        label = { Text("Exact") },
                        leadingIcon = if (state.matchMode == DuplicateMatchMode.EXACT) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.matchMode == DuplicateMatchMode.CONTAINS,
                        onClick = { screenModel.setMatchMode(DuplicateMatchMode.CONTAINS) },
                        label = { Text("Contains") },
                        leadingIcon = if (state.matchMode == DuplicateMatchMode.CONTAINS) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.matchMode == DuplicateMatchMode.URL,
                        onClick = { screenModel.setMatchMode(DuplicateMatchMode.URL) },
                        label = { Text("Same URL") },
                        leadingIcon = if (state.matchMode == DuplicateMatchMode.URL) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }

                // Content type selector (Manga/Novel/Both)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Type:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    FilterChip(
                        selected = state.contentType == DuplicateDetectionScreenModel.ContentType.ALL,
                        onClick = { screenModel.setContentType(DuplicateDetectionScreenModel.ContentType.ALL) },
                        label = { Text("All") },
                        leadingIcon = if (state.contentType == DuplicateDetectionScreenModel.ContentType.ALL) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.contentType == DuplicateDetectionScreenModel.ContentType.MANGA,
                        onClick = { screenModel.setContentType(DuplicateDetectionScreenModel.ContentType.MANGA) },
                        label = { Text("Manga") },
                        leadingIcon = if (state.contentType == DuplicateDetectionScreenModel.ContentType.MANGA) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.contentType == DuplicateDetectionScreenModel.ContentType.NOVEL,
                        onClick = { screenModel.setContentType(DuplicateDetectionScreenModel.ContentType.NOVEL) },
                        label = { Text("Novel") },
                        leadingIcon = if (state.contentType == DuplicateDetectionScreenModel.ContentType.NOVEL) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }

                // Show URLs toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { screenModel.toggleShowFullUrls() }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.showFullUrls,
                        onCheckedChange = { screenModel.toggleShowFullUrls() },
                    )
                    Text(
                        "Show full URLs",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // Sort mode selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Sort:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    FilterChip(
                        selected = state.sortMode == DuplicateDetectionScreenModel.SortMode.NAME,
                        onClick = { screenModel.setSortMode(DuplicateDetectionScreenModel.SortMode.NAME) },
                        label = { Text("Name") },
                        leadingIcon = if (state.sortMode == DuplicateDetectionScreenModel.SortMode.NAME) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.sortMode == DuplicateDetectionScreenModel.SortMode.LATEST_ADDED,
                        onClick = { screenModel.setSortMode(DuplicateDetectionScreenModel.SortMode.LATEST_ADDED) },
                        label = { Text("Latest") },
                        leadingIcon = if (state.sortMode == DuplicateDetectionScreenModel.SortMode.LATEST_ADDED) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.sortMode == DuplicateDetectionScreenModel.SortMode.CHAPTER_COUNT_DESC,
                        onClick = { screenModel.setSortMode(DuplicateDetectionScreenModel.SortMode.CHAPTER_COUNT_DESC) },
                        label = { Text("Ch↓") },
                        leadingIcon = if (state.sortMode == DuplicateDetectionScreenModel.SortMode.CHAPTER_COUNT_DESC) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.sortMode == DuplicateDetectionScreenModel.SortMode.DOWNLOAD_COUNT_DESC,
                        onClick = { screenModel.setSortMode(DuplicateDetectionScreenModel.SortMode.DOWNLOAD_COUNT_DESC) },
                        label = { Text("DL↓") },
                        leadingIcon = if (state.sortMode == DuplicateDetectionScreenModel.SortMode.DOWNLOAD_COUNT_DESC) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.sortMode == DuplicateDetectionScreenModel.SortMode.READ_COUNT_DESC,
                        onClick = { screenModel.setSortMode(DuplicateDetectionScreenModel.SortMode.READ_COUNT_DESC) },
                        label = { Text("Read↓") },
                        leadingIcon = if (state.sortMode == DuplicateDetectionScreenModel.SortMode.READ_COUNT_DESC) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.sortMode == DuplicateDetectionScreenModel.SortMode.PINNED_SOURCE,
                        onClick = { screenModel.setSortMode(DuplicateDetectionScreenModel.SortMode.PINNED_SOURCE) },
                        label = { 
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        leadingIcon = if (state.sortMode == DuplicateDetectionScreenModel.SortMode.PINNED_SOURCE) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }

                // Category filters
                if (state.categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Category:",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        if (state.selectedCategoryFilters.isNotEmpty()) {
                            FilterChip(
                                selected = true,
                                onClick = { screenModel.clearCategoryFilters() },
                                label = { Text("Clear") },
                            )
                        }
                        state.categories.take(5).forEach { category ->
                            FilterChip(
                                selected = category.id in state.selectedCategoryFilters,
                                onClick = { screenModel.toggleCategoryFilter(category.id) },
                                label = { Text(category.name) },
                                leadingIcon = if (category.id in state.selectedCategoryFilters) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                }

                when {
                    !state.hasStartedAnalysis -> {
                        // Initial state - show start analysis button
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Find Duplicate Novels",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    text = "Analyze your library to find duplicate entries.\nSelect a match mode above and click Start.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { screenModel.loadDuplicates() },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Analysis")
                                }
                            }
                        }
                    }
                    state.isLoading -> {
                        LoadingScreen()
                    }
                    state.filteredDuplicateGroups.isEmpty() -> {
                        EmptyScreen(message = if (state.duplicateGroups.isEmpty()) "No duplicates found in your library." else "No duplicates match the selected filters.")
                    }
                    else -> {
                        // Results summary
                        Text(
                            text = "Found ${state.filteredDuplicateGroups.size} groups with ${state.filteredDuplicateGroups.values.sumOf { it.size }} potential duplicates" +
                                if (state.selectedCategoryFilters.isNotEmpty()) " (filtered)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.filteredDuplicateGroups.toList()) { (title, mangaList) ->
                                DuplicateGroupCard(
                                    groupTitle = title,
                                    mangaList = mangaList,
                                    selection = state.selection,
                                    mangaCategories = state.mangaCategories,
                                    showFullUrls = state.showFullUrls,
                                    onToggleSelection = { screenModel.toggleSelection(it) },
                                    onSelectGroup = { screenModel.selectGroup(title) },
                                    onClickManga = { navigator.push(MangaScreen(it)) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (state.showDeleteDialog) {
            DeleteSelectedDialog(
                count = state.selection.size,
                onDismiss = { screenModel.closeDeleteDialog() },
                onConfirm = { deleteManga, deleteChapters ->
                    scope.launch {
                        screenModel.deleteSelected(deleteManga, deleteChapters)
                        snackbarHostState.showSnackbar("Deleted ${state.selection.size} novels")
                    }
                },
            )
        }

        // Move to category dialog
        if (state.showMoveToCategoryDialog) {
            MoveToCategoryDialog(
                categories = state.categories,
                onDismiss = { screenModel.closeMoveToCategoryDialog() },
                onConfirm = { categoryIds ->
                    scope.launch {
                        screenModel.moveSelectedToCategories(categoryIds)
                        snackbarHostState.showSnackbar("Moved ${state.selection.size} novels")
                    }
                },
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    groupTitle: String,
    mangaList: List<MangaWithChapterCount>,
    selection: Set<Long>,
    mangaCategories: Map<Long, List<Category>>,
    showFullUrls: Boolean,
    onToggleSelection: (Long) -> Unit,
    onSelectGroup: () -> Unit,
    onClickManga: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupTitle.replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${mangaList.size} novels in this group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Select Group button
                IconButton(onClick = onSelectGroup) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = "Select Group",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            if (expanded) {
                HorizontalDivider()
                mangaList.forEachIndexed { index, mangaWithCount ->
                    DuplicateItem(
                        manga = mangaWithCount,
                        categories = mangaCategories[mangaWithCount.manga.id] ?: emptyList(),
                        isSelected = mangaWithCount.manga.id in selection,
                        isFirst = index == 0,
                        showFullUrl = showFullUrls,
                        onToggleSelection = { onToggleSelection(mangaWithCount.manga.id) },
                        onClick = { onClickManga(mangaWithCount.manga.id) },
                    )
                    if (index < mangaList.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateItem(
    manga: MangaWithChapterCount,
    categories: List<Category>,
    isSelected: Boolean,
    isFirst: Boolean,
    showFullUrl: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
) {
    val sourceManager: SourceManager = remember { Injekt.get() }
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val sourceName = remember(manga.manga.source) {
        sourceManager.getOrStub(manga.manga.source).getNameForMangaInfo()
    }
    val downloadedCount = remember(manga.manga.id) {
        downloadManager.getDownloadCount(manga.manga)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = manga.manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal,
                    color = if (isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isFirst) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ORIGINAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // First row: chapters and source
            Row {
                Text(
                    text = "${manga.chapterCount} ch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (downloadedCount > 0) {
                    Text(
                        text = " • $downloadedCount dl",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (manga.readCount > 0) {
                    Text(
                        text = " • ${manga.readCount} read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Show source/plugin name
                Text(
                    text = " • $sourceName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Show author if available
                manga.manga.author?.let { author ->
                    Text(
                        text = " • $author",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            
            // Second row: categories
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = "Categories: ${categories.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            
            // Third row: full URL (when enabled)
            if (showFullUrl) {
                Spacer(modifier = Modifier.height(2.dp))
                val source = remember(manga.manga.source) { sourceManager.getOrStub(manga.manga.source) }
                val fullUrl = remember(manga.manga.url, source) {
                    if (manga.manga.url.startsWith("http://") || manga.manga.url.startsWith("https://")) {
                        manga.manga.url
                    } else if (source is eu.kanade.tachiyomi.source.online.HttpSource) {
                        try {
                            source.getMangaUrl(manga.manga.toSManga())
                        } catch (_: Exception) {
                            source.baseUrl + manga.manga.url
                        }
                    } else {
                        manga.manga.url
                    }
                }
                Text(
                    text = fullUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeleteSelectedDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (deleteManga: Boolean, deleteChapters: Boolean) -> Unit,
) {
    var deleteManga by remember { mutableStateOf(false) }
    var deleteChapters by remember { mutableStateOf(true) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $count novels?") },
        text = {
            Column {
                Text("This will remove the selected novels from your library.")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { deleteChapters = !deleteChapters },
                ) {
                    Checkbox(checked = deleteChapters, onCheckedChange = { deleteChapters = it })
                    Text("Delete downloaded chapters")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { deleteManga = !deleteManga },
                ) {
                    Checkbox(checked = deleteManga, onCheckedChange = { deleteManga = it })
                    Text("Delete manga from database")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteManga, deleteChapters); onDismiss() }) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MoveToCategoryDialog(
    categories: List<tachiyomi.domain.category.model.Category>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    var selectedCategories by remember { mutableStateOf(setOf<Long>()) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Category") },
        text = {
            LazyColumn {
                items(categories) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategories = if (category.id in selectedCategories) {
                                    selectedCategories - category.id
                                } else {
                                    selectedCategories + category.id
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = category.id in selectedCategories,
                            onCheckedChange = {
                                selectedCategories = if (it) {
                                    selectedCategories + category.id
                                } else {
                                    selectedCategories - category.id
                                }
                            },
                        )
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCategories.toList()); onDismiss() },
                enabled = selectedCategories.isNotEmpty(),
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
