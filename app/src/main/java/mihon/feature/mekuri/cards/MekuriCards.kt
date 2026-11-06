package mihon.feature.mekuri.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData

private fun LibraryItem.coverData() = libraryManga.manga.let { manga ->
    MangaCoverData(
        mangaId = manga.id,
        sourceId = manga.source,
        isMangaFavorite = manga.favorite,
        url = manga.thumbnailUrl,
        lastModified = manga.coverLastModified,
    )
}

private fun LibraryItem.progress(): Float {
    val total = libraryManga.totalChapters
    return if (total <= 0) 0f else (libraryManga.readCount.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Mekuri "cards": rounded covers with a coral unread count, the title, and a thin reading-progress bar.
 */
@Composable
internal fun LibraryMekuriCards(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(modifier = Modifier.fillMaxSize(), columns = columns, contentPadding = contentPadding) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)
        items(items = items, contentType = { "mekuri_card" }) { item ->
            MekuriCard(
                item = item,
                selected = item.id in selection,
                onClick = { onClick(item.libraryManga) },
                onLongClick = { onLongClick(item.libraryManga) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MekuriCard(item: LibraryItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .then(if (selected) Modifier.background(scheme.secondaryContainer) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            MangaCover.Book(
                data = item.coverData(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (selected) {
                            Modifier.border(
                                BorderStroke(2.dp, scheme.primary),
                                RoundedCornerShape(14.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            if (item.unreadCount > 0) {
                Text(
                    text = item.unreadCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.secondary)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                        .semantics { contentDescription = "${item.unreadCount} unread" },
                )
            }
            if (item.downloadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiary)
                        .semantics { contentDescription = "Downloaded" },
                )
            }
        }
        Text(
            text = item.libraryManga.manga.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        LinearProgressIndicator(
            progress = { item.progress() },
            color = scheme.secondary,
            trackColor = scheme.onSurface.copy(alpha = 0.1f),
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .height(3.dp),
        )
    }
}

/**
 * Mekuri "wide cards": one manga per row with its cover, progress through the series, unread count and a resume
 * button.
 */
@Composable
internal fun LibraryMekuriWideCards(
    items: List<LibraryItem>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(modifier = Modifier.fillMaxSize(), columns = 1, contentPadding = contentPadding) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)
        items(items = items, contentType = { "mekuri_wide_card" }, span = { GridItemSpan(maxLineSpan) }) { item ->
            WideCard(
                item = item,
                selected = item.id in selection,
                onClick = { onClick(item.libraryManga) },
                onLongClick = { onLongClick(item.libraryManga) },
                onContinue = onClickContinueReading?.takeIf { item.unreadCount > 0 }?.let { { it(item.libraryManga) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideCard(
    item: LibraryItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContinue: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val manga = item.libraryManga
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MangaCover.Book(
                data = item.coverData(),
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = manga.manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("Ch. ${manga.readCount} of ${manga.totalChapters}")
                        if (item.unreadCount > 0) append(" · ${item.unreadCount} unread")
                        if (item.downloadCount > 0) append(" · ${item.downloadCount} offline")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { item.progress() },
                    color = scheme.secondary,
                    trackColor = scheme.onSurface.copy(alpha = 0.1f),
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
                Text(
                    text = item.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 1,
                )
            }
            if (onContinue != null) {
                FilledTonalIconButton(onClick = onContinue) {
                    Icon(
                        imageVector = MaterialSymbols.RoundedFilled.PlayArrow,
                        contentDescription = "Continue reading ${manga.manga.title}",
                    )
                }
            }
        }
    }
}
