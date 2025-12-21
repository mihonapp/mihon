package eu.kanade.tachiyomi.ui.download

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import kotlin.math.roundToInt

private enum class DownloadQueueFilter {
    All,
    Active,
    Errors,
}

object DownloadQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { DownloadQueueScreenModel() }
        val mangaList by screenModel.state.collectAsState()
        val novelList by screenModel.novelState.collectAsState()
        val titleMaxLines by screenModel.titleMaxLines.collectAsState()

        var selectedTab by remember { mutableStateOf(0) }
        var filterMode by remember { mutableStateOf(DownloadQueueFilter.All) }
        val canReorder = filterMode == DownloadQueueFilter.All

        val filteredMangaList by remember(mangaList, filterMode) {
            derivedStateOf {
                if (filterMode == DownloadQueueFilter.All) return@derivedStateOf mangaList

                mangaList.mapNotNull { header ->
                    val filteredDownloads = header.subItems
                        .map { it.download }
                        .filter {
                            when (filterMode) {
                                DownloadQueueFilter.Active ->
                                    it.status == Download.State.DOWNLOADING ||
                                        it.status == Download.State.QUEUE
                                DownloadQueueFilter.Errors -> it.status == Download.State.ERROR
                                DownloadQueueFilter.All -> true
                            }
                        }

                    if (filteredDownloads.isEmpty()) return@mapNotNull null

                    DownloadHeaderItem(
                        id = header.id,
                        name = header.name,
                        size = filteredDownloads.size,
                    ).apply {
                        addSubItems(0, filteredDownloads.map { DownloadItem(it, this) })
                    }
                }
            }
        }

        val filteredNovelList by remember(novelList, filterMode) {
            derivedStateOf {
                when (filterMode) {
                    DownloadQueueFilter.All -> novelList
                    DownloadQueueFilter.Active -> novelList.filter {
                        it.isActive ||
                            it.subItems.any { d -> d.status == Download.State.QUEUE }
                    }
                    DownloadQueueFilter.Errors -> novelList.filter { it.hasError }
                }
            }
        }

        val mangaCount by remember {
            derivedStateOf { mangaList.sumOf { it.subItems.size } }
        }
        val novelCount by remember {
            derivedStateOf { novelList.sumOf { it.totalChapters } }
        }

        val tabs = listOf(
            "${stringResource(MR.strings.label_manga)} ($mangaCount)",
            "${stringResource(MR.strings.label_novels)} ($novelCount)",
        )

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var fabExpanded by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            // All this lines just for fab state :/
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    fabExpanded = available.y >= 0
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPreFling(available)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(MR.strings.label_download_queue),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, false),
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        if ((selectedTab == 0 && filteredMangaList.isNotEmpty()) ||
                            (selectedTab == 1 && filteredNovelList.isNotEmpty())
                        ) {
                            var sortExpanded by remember { mutableStateOf(false) }
                            var filterExpanded by remember { mutableStateOf(false) }
                            val onDismissRequest = { sortExpanded = false }

                            DropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.all)) },
                                    onClick = {
                                        filterMode = DownloadQueueFilter.All
                                        filterExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.ext_downloading)) },
                                    onClick = {
                                        filterMode = DownloadQueueFilter.Active
                                        filterExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(MR.strings.channel_errors)) },
                                    onClick = {
                                        filterMode = DownloadQueueFilter.Errors
                                        filterExpanded = false
                                    },
                                )
                            }

                            DropdownMenu(
                                expanded = sortExpanded && canReorder,
                                onDismissRequest = onDismissRequest,
                            ) {
                                if (selectedTab == 1) {
                                    // Novel queue sorts (series-level)
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_progress))
                                        },
                                        onClick = {
                                            val order = novelList
                                                .sortedByDescending { it.overallProgress }
                                                .map { it.mangaId }
                                            screenModel.reorderNovelQueueByGroupOrder(order)
                                            onDismissRequest()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_total_chapters))
                                        },
                                        onClick = {
                                            val order = novelList
                                                .sortedByDescending { it.totalChapters }
                                                .map { it.mangaId }
                                            screenModel.reorderNovelQueueByGroupOrder(order)
                                            onDismissRequest()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_extension))
                                        },
                                        onClick = {
                                            val order = novelList
                                                .sortedWith(
                                                    compareBy<NovelDownloadItem>(
                                                        { it.sourceName.lowercase() },
                                                        { it.mangaTitle.lowercase() },
                                                    ),
                                                )
                                                .map { it.mangaId }
                                            screenModel.reorderNovelQueueByGroupOrder(order)
                                            onDismissRequest()
                                        },
                                    )
                                } else {
                                    NestedMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_upload_date))
                                        },
                                        children = { closeMenu ->
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_newest)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.chapterDateUpload },
                                                        true,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_oldest)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.chapterDateUpload },
                                                        false,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                        },
                                    )
                                    NestedMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_chapter_number))
                                        },
                                        children = { closeMenu ->
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_asc)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.chapterNumber },
                                                        false,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_desc)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.chapterNumber },
                                                        true,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                        },
                                    )

                                    NestedMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_progress))
                                        },
                                        children = { closeMenu ->
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_desc)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.progress },
                                                        true,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_asc)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.progress },
                                                        false,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                        },
                                    )

                                    NestedMenuItem(
                                        text = {
                                            Text(text = stringResource(MR.strings.action_order_by_extension))
                                        },
                                        children = { closeMenu ->
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_asc)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.source.name.lowercase() },
                                                        false,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MR.strings.action_desc)) },
                                                onClick = {
                                                    screenModel.reorderQueue(
                                                        { it.download.source.name.lowercase() },
                                                        true,
                                                    )
                                                    closeMenu()
                                                },
                                            )
                                        },
                                    )
                                }
                            }

                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_filter),
                                        icon = Icons.Outlined.FilterList,
                                        onClick = { filterExpanded = true },
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_sort),
                                        icon = Icons.AutoMirrored.Outlined.Sort,
                                        onClick = { if (canReorder) sortExpanded = true },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_cancel_all),
                                        onClick = { screenModel.clearQueue() },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible =
                    (selectedTab == 0 && mangaList.isNotEmpty()) ||
                        (selectedTab == 1 && novelList.isNotEmpty()),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    val isRunning by screenModel.isDownloaderRunning.collectAsState()
                    ExtendedFloatingActionButton(
                        text = {
                            val id = if (isRunning) {
                                MR.strings.action_pause
                            } else {
                                MR.strings.action_resume
                            }
                            Text(text = stringResource(id))
                        },
                        icon = {
                            val icon = if (isRunning) {
                                Icons.Outlined.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            }
                            Icon(imageVector = icon, contentDescription = null)
                        },
                        onClick = {
                            if (isRunning) {
                                screenModel.pauseDownloads()
                            } else {
                                screenModel.startDownloads()
                            }
                        },
                        expanded = fabExpanded,
                    )
                }
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, titleRes ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(titleRes) },
                        )
                    }
                }

                if ((selectedTab == 0 && filteredMangaList.isEmpty()) ||
                    (selectedTab == 1 && filteredNovelList.isEmpty())
                ) {
                    EmptyScreen(
                        stringRes = MR.strings.information_no_downloads,
                    )
                } else {
                    val density = LocalDensity.current
                    val layoutDirection = LocalLayoutDirection.current
                    val left = with(density) {
                        contentPadding.calculateLeftPadding(layoutDirection).toPx().roundToInt()
                    }
                    val right = with(density) {
                        contentPadding.calculateRightPadding(layoutDirection).toPx().roundToInt()
                    }
                    val bottom = with(density) { contentPadding.calculateBottomPadding().toPx().roundToInt() }

                    Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                        if (selectedTab == 0) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { context ->
                                    screenModel.controllerBinding =
                                        DownloadListBinding.inflate(LayoutInflater.from(context))
                                    screenModel.adapter = DownloadAdapter(screenModel.listener)
                                    screenModel.controllerBinding.root.adapter = screenModel.adapter
                                    screenModel.adapter?.isHandleDragEnabled = canReorder
                                    screenModel.controllerBinding.root.layoutManager =
                                        LinearLayoutManager(context)

                                    ViewCompat.setNestedScrollingEnabled(
                                        screenModel.controllerBinding.root,
                                        true,
                                    )

                                    scope.launchUI {
                                        screenModel.getDownloadStatusFlow()
                                            .collect(screenModel::onStatusChange)
                                    }
                                    scope.launchUI {
                                        screenModel.getDownloadProgressFlow()
                                            .collect(screenModel::onUpdateDownloadedPages)
                                    }

                                    screenModel.controllerBinding.root
                                },
                                update = {
                                    screenModel.controllerBinding.root
                                        .updatePadding(
                                            left = left,
                                            top = 0,
                                            right = right,
                                            bottom = bottom,
                                        )

                                    screenModel.adapter?.isHandleDragEnabled = canReorder
                                    screenModel.adapter?.updateDataSet(filteredMangaList)
                                },
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = 8.dp,
                                    bottom = 80.dp,
                                    start = 16.dp,
                                    end = 16.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    items = filteredNovelList,
                                    key = { it.mangaId },
                                ) { item ->
                                    NovelDownloadCard(
                                        item = item,
                                        titleMaxLines = titleMaxLines,
                                        onCancel = { screenModel.cancel(item.subItems) },
                                        onMoveToTop = {
                                            screenModel.reorder(
                                                item.subItems + (
                                                    novelList.flatMap {
                                                        it.subItems
                                                    } - item.subItems.toSet()
                                                    ),
                                            )
                                        },
                                        onMoveToBottom = {
                                            screenModel.reorder(
                                                (
                                                    novelList.flatMap {
                                                        it.subItems
                                                    } - item.subItems.toSet()
                                                    ) + item.subItems,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelDownloadCard(
    item: NovelDownloadItem,
    titleMaxLines: Int,
    onCancel: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
) {
    val context = LocalContext.current
    val errorLabel = stringResource(MR.strings.download_error_details)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item.mangaTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.downloadedChapters}/${item.totalChapters} chapters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move to top") },
                            onClick = {
                                onMoveToTop()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move to bottom") },
                            onClick = {
                                onMoveToBottom()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel") },
                            onClick = {
                                onCancel()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { item.overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
                color = when {
                    item.hasError -> MaterialTheme.colorScheme.error
                    item.isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        item.hasError -> MaterialTheme.colorScheme.error
                        item.isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                val currentDownload = item.currentDownload
                if (currentDownload != null) {
                    Text(
                        text = "Chapter: ${currentDownload.chapterName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
                    )
                }

                Text(
                    text = "${(item.overallProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val errorDetails: String? = remember(item.subItems) {
                item.subItems.firstOrNull { it.status == Download.State.ERROR }
                    ?.error
                    ?.takeIf { it.isNotBlank() }
            }
            if (errorDetails != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        context.copyToClipboard(
                            label = errorLabel,
                            content = errorDetails,
                        )
                    },
                )
            }
        }
    }
}
