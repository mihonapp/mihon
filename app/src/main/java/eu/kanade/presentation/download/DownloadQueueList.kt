package eu.kanade.presentation.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.download.DownloadQueueSection
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.MoreVert
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberScroller
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadQueueList(
    sections: List<DownloadQueueSection>,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    onReorder: (List<Download>) -> Unit,
    onMove: (Download, toTop: Boolean) -> Unit,
    onMoveSeries: (Download, toTop: Boolean) -> Unit,
    onCancel: (Download) -> Unit,
    onCancelSeries: (Download) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionsState = remember { sections.toMutableStateList() }
    // While a section is dragged its downloads are hidden, so it moves past whole sections
    var isSectionDragging by remember { mutableStateOf(false) }
    // While a download is dragged only its own section's rows can be swapped with it
    var draggingSectionId by remember { mutableStateOf<Long?>(null) }
    // The release animation starts from the finger, so the last dragged row stays held while it settles too
    var lastDraggedKey by remember { mutableStateOf<String?>(null) }
    val listCoordinates = remember { CoordinatesHolder() }
    val draggingSection by rememberUpdatedState(sectionsState.find { it.id == draggingSectionId })
    // Auto-scroll would otherwise carry a download held at its section's edge off screen
    val sectionScrollState = remember(lazyListState) {
        LimitedScrollableState(lazyListState) { pixels ->
            draggingSection?.let { lazyListState.limitScrollToSection(it, pixels) } ?: pixels
        }
    }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = contentPadding,
        scroller = rememberScroller(
            scrollableState = sectionScrollState,
            pixelAmountProvider = { lazyListState.layoutInfo.viewportSize.height * 0.05f },
        ),
    ) { from, to ->
        val fromKey = from.key as String
        val toKey = to.key as String
        when {
            fromKey.startsWith(SECTION_KEY) && toKey.startsWith(SECTION_KEY) -> {
                val fromIndex = sectionsState.indexOfFirst { it.key == fromKey }
                val toIndex = sectionsState.indexOfFirst { it.key == toKey }
                sectionsState.add(toIndex, sectionsState.removeAt(fromIndex))
            }
            // Downloads don't change section
            fromKey.startsWith(DOWNLOAD_KEY) && toKey.startsWith(DOWNLOAD_KEY) -> {
                val sectionIndex = sectionsState.indexOfFirst { section ->
                    section.downloads.any { it.key == fromKey }
                }
                val section = sectionsState[sectionIndex]
                val toIndex = section.downloads.indexOfFirst { it.key == toKey }
                if (toIndex != -1) {
                    val downloads = section.downloads.toMutableList()
                    val fromIndex = downloads.indexOfFirst { it.key == fromKey }
                    downloads.add(toIndex, downloads.removeAt(fromIndex))
                    sectionsState[sectionIndex] = section.copy(downloads = downloads)
                }
            }
        }
    }

    LaunchedEffect(sections) {
        if (!reorderableState.isAnyItemDragging) {
            sectionsState.clear()
            sectionsState.addAll(sections)
        }
    }

    val onDragStopped = {
        isSectionDragging = false
        draggingSectionId = null
        onReorder(sectionsState.flatMap { it.downloads })
    }

    val firstDownload = sectionsState.firstOrNull()?.downloads?.firstOrNull()
    val lastDownload = sectionsState.lastOrNull()?.downloads?.lastOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { listCoordinates.value = it },
        state = lazyListState,
        contentPadding = contentPadding,
    ) {
        sectionsState.forEach { section ->
            item(key = section.key) {
                ReorderableItem(reorderableState, section.key, enabled = draggingSectionId == null) { isDragging ->
                    DownloadQueueHeader(
                        section = section,
                        isDragging = isDragging,
                        onDragStarted = { isSectionDragging = true },
                        onDragStopped = onDragStopped,
                    )
                }
            }
            if (!isSectionDragging) {
                items(
                    items = section.downloads,
                    key = { it.key },
                ) { download ->
                    ReorderableItem(
                        state = reorderableState,
                        key = download.key,
                        enabled = draggingSectionId == null || draggingSectionId == section.id,
                    ) { isDragging ->
                        DownloadQueueItem(
                            download = download,
                            isDragging = isDragging,
                            modifier = if (isDragging || download.key == lastDraggedKey) {
                                Modifier.keepInSection(
                                    lazyListState = lazyListState,
                                    listCoordinates = listCoordinates,
                                    sectionKey = section.key,
                                    lastDownloadKey = section.downloads.last().key,
                                )
                            } else {
                                Modifier
                            },
                            canMoveToTop = download != firstDownload,
                            canMoveToBottom = download != lastDownload,
                            onDragStarted = {
                                draggingSectionId = section.id
                                lastDraggedKey = download.key
                            },
                            onDragStopped = onDragStopped,
                            onMove = { toTop -> onMove(download, toTop) },
                            onMoveSeries = { toTop -> onMoveSeries(download, toTop) },
                            onCancel = { onCancel(download) },
                            onCancelSeries = { onCancelSeries(download) },
                        )
                    }
                }
            }
        }
    }
}

// Lazy list keys must fit in a Bundle
private const val SECTION_KEY = "section-"
private const val DOWNLOAD_KEY = "download-"
private val DownloadQueueSection.key get() = "$SECTION_KEY$id"
private val Download.key get() = "$DOWNLOAD_KEY${chapter.id}"

private class CoordinatesHolder {
    var value: LayoutCoordinates? = null
}

/**
 * [lazyListState], with every scroll step passed through [limit] first.
 */
private class LimitedScrollableState(
    private val lazyListState: LazyListState,
    private val limit: (Float) -> Float,
) : ScrollableState by lazyListState {
    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        lazyListState.scroll(scrollPriority) {
            val scope = this
            object : ScrollScope {
                override fun scrollBy(pixels: Float) = scope.scrollBy(limit(pixels))
            }.block()
        }
    }
}

/**
 * Limits [pixels] so the list scrolls no further than the end of [section] reaching the bottom, or its header
 * reaching the top, while that edge is on screen.
 */
private fun LazyListState.limitScrollToSection(section: DownloadQueueSection, pixels: Float): Float {
    val items = layoutInfo.visibleItemsInfo
    return if (pixels > 0) {
        val last = items.firstOrNull { it.key == section.downloads.last().key } ?: return pixels
        val contentEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        pixels.coerceAtMost((last.offset + last.size - contentEnd).coerceAtLeast(0).toFloat())
    } else {
        val header = items.firstOrNull { it.key == section.key } ?: return pixels
        pixels.coerceAtLeast((header.offset).coerceAtMost(0).toFloat())
    }
}

/**
 * Stops a dragged download at the edges of its section, between the header and the section's last slot, instead of
 * letting it float over another source it can't be dropped in.
 */
@Composable
private fun Modifier.keepInSection(
    lazyListState: LazyListState,
    listCoordinates: CoordinatesHolder,
    sectionKey: String,
    lastDownloadKey: String,
): Modifier {
    var correction by remember { mutableFloatStateOf(0f) }
    return this
        // Before the correction layer, so it sees where the drag put the row
        .onGloballyPositioned { coordinates ->
            val list = listCoordinates.value?.takeIf { it.isAttached } ?: return@onGloballyPositioned
            val top = list.localPositionOf(coordinates, Offset.Zero).y
            val layoutInfo = lazyListState.layoutInfo
            val itemStart = layoutInfo.beforeContentPadding
            val header = layoutInfo.visibleItemsInfo.firstOrNull { it.key == sectionKey }
            val last = layoutInfo.visibleItemsInfo.firstOrNull { it.key == lastDownloadKey }
            val minTop = header?.let { (itemStart + it.offset + it.size).toFloat() } ?: Float.NEGATIVE_INFINITY
            val maxTop = last?.let { (itemStart + it.offset + it.size - coordinates.size.height).toFloat() }
                ?: Float.POSITIVE_INFINITY
            correction = top.coerceIn(minTop, maxOf(minTop, maxTop)) - top
        }
        .graphicsLayer { translationY = correction }
}

@Composable
private fun ReorderableCollectionItemScope.DownloadQueueHeader(
    section: DownloadQueueSection,
    isDragging: Boolean,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(top = MaterialTheme.padding.small),
        color = listItemContainerColor(isDragging),
        shadowElevation = listItemElevation(isDragging),
    ) {
        Row(
            modifier = Modifier.padding(end = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListGroupHeader(
                text = "${section.name} (${section.downloads.size})",
                modifier = Modifier.weight(1f),
            )
            DragHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped)
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.DownloadQueueItem(
    download: Download,
    isDragging: Boolean,
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    onMove: (toTop: Boolean) -> Unit,
    onMoveSeries: (toTop: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCancelSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by remember(download) { download.pagesProgressFlow() }.collectAsState(null)
    val animatedProgress by animateFloatAsState(progress?.fraction ?: 0f, label = "progress")

    Surface(
        modifier = modifier,
        color = listItemContainerColor(isDragging),
        shadowElevation = listItemElevation(isDragging),
    ) {
        Row(
            modifier = Modifier.padding(
                start = MaterialTheme.padding.small,
                end = MaterialTheme.padding.extraSmall,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MaterialTheme.padding.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    Text(
                        text = download.manga.customTitle?.takeIf { it.isNotBlank() } ?: download.manga.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    progress?.let {
                        Text(
                            text = "${it.downloadedPages}/${it.pageCount}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    text = download.chapter.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.extraSmall),
                )
            }
            DownloadQueueItemMenu(
                canMoveToTop = canMoveToTop,
                canMoveToBottom = canMoveToBottom,
                onMove = onMove,
                onMoveSeries = onMoveSeries,
                onCancel = onCancel,
                onCancelSeries = onCancelSeries,
            )
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.DragHandle(onDragStarted: () -> Unit, onDragStopped: () -> Unit) {
    Icon(
        imageVector = MaterialSymbols.Rounded.DragHandle,
        contentDescription = null,
        modifier = Modifier
            .draggableHandle(
                onDragStarted = { onDragStarted() },
                onDragStopped = onDragStopped,
            )
            .padding(MaterialTheme.padding.small),
    )
}

@Composable
private fun DownloadQueueItemMenu(
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    onMove: (toTop: Boolean) -> Unit,
    onMoveSeries: (toTop: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCancelSeries: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val closeMenu = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.MoreVert,
                contentDescription = stringResource(MR.strings.action_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
            if (canMoveToTop) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_top)) },
                    onClick = {
                        closeMenu()
                        onMove(true)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_move_to_top_all_for_series)) },
                onClick = {
                    closeMenu()
                    onMoveSeries(true)
                },
            )
            if (canMoveToBottom) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_bottom)) },
                    onClick = {
                        closeMenu()
                        onMove(false)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_move_to_bottom_all_for_series)) },
                onClick = {
                    closeMenu()
                    onMoveSeries(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_cancel)) },
                onClick = {
                    closeMenu()
                    onCancel()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.cancel_all_for_series)) },
                onClick = {
                    closeMenu()
                    onCancelSeries()
                },
            )
        }
    }
}

// A neutral lifted surface while dragged, rather than Material's tertiaryContainer list tint
@Composable
private fun listItemContainerColor(isDragging: Boolean): Color {
    return if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background
}

@Composable
private fun listItemElevation(isDragging: Boolean): Dp {
    val elevation = ListItemDefaults.elevation()
    return if (isDragging) elevation.draggedElevation else elevation.elevation
}

private class PagesProgress(
    val pageCount: Int,
    val downloadedPages: Int,
    totalProgress: Int,
) {
    val fraction = if (pageCount == 0) 0f else totalProgress / (pageCount * 100f)
}

/**
 * Progress of the pages of this download, or null until its pages are known.
 */
private fun Download.pagesProgressFlow() = pagesFlow.flatMapLatest { pages ->
    when {
        pages == null -> flowOf(null)
        pages.isEmpty() -> flowOf(PagesProgress(pageCount = 0, downloadedPages = 0, totalProgress = 0))
        else ->
            combine(pages.map { page -> combine(page.statusFlow, page.progressFlow, ::Pair) }) { states ->
                PagesProgress(
                    pageCount = pages.size,
                    downloadedPages = states.count { (status, _) -> status == Page.State.Ready },
                    totalProgress = states.sumOf { (_, progress) -> progress },
                )
            }
    }
}
