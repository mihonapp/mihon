package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import mihon.reader.layout.PageGrouping
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderState

data class ReaderSpread(val pageIndices: List<Int>)

fun ReaderState.visibleSpread(): ReaderSpread {
    if (pages.isEmpty()) return ReaderSpread(emptyList())
    val groups = PageGrouping.forMode(pages, mode, coverOffset)
    val selected = pages[selectedIndex].id
    val group = groups.firstOrNull { pagesInGroup -> pagesInGroup.any { it.id == selected } }
        ?: groups.first()
    return ReaderSpread(group.map { page -> pages.indexOfFirst { it.id == page.id } })
}

@Composable
internal fun PagedReader(
    state: ReaderState,
    viewportWidth: Dp,
    onAction: (ReaderAction) -> Unit,
    pageContent: ReaderPageContent,
    modifier: Modifier = Modifier,
    pageSizes: Map<PageId, PageSize> = emptyMap(),
) {
    val spread = state.visibleSpread()
    val visiblePageIds = spread.pageIndices.map { index -> state.pages[index].id }
    LaunchedEffect(state.chapterId, visiblePageIds) {
        onAction(ReaderAction.SetVisiblePages(visiblePageIds))
    }
    when {
        spread.pageIndices.size == 2 -> Row(modifier = modifier.fillMaxSize()) {
            spread.pageIndices.forEach { pageIndex ->
                val page = state.pages[pageIndex]
                val intrinsicSize = pageSizes[page.id]
                ReaderPageFrame(
                    page = page.withIntrinsicSize(intrinsicSize),
                    pageIndex = pageIndex,
                    totalPages = state.pages.size,
                    scaleMode = state.scaleMode,
                    zoom = state.zoom,
                    requestedPan = state.pan,
                    pageContent = pageContent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    intrinsicSize = intrinsicSize,
                )
            }
        }
        spread.pageIndices.size == 1 -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val pageIndex = spread.pageIndices.single()
            val page = state.pages[pageIndex]
            val intrinsicSize = pageSizes[page.id]
            ReaderPageFrame(
                page = page.withIntrinsicSize(intrinsicSize),
                pageIndex = pageIndex,
                totalPages = state.pages.size,
                scaleMode = state.scaleMode,
                zoom = state.zoom,
                requestedPan = state.pan,
                pageContent = pageContent,
                modifier = if (state.mode.isDualPage) {
                    Modifier.width(viewportWidth / 2).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                },
                intrinsicSize = intrinsicSize,
            )
        }
    }
}
