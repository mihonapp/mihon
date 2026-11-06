package eu.kanade.presentation.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.NeonDefaults
import eu.kanade.presentation.theme.neonGlow
import eu.kanade.presentation.theme.neonOutline
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

private const val MAX_CONTINUE_READING = 8

private val HERO_CARD_HEIGHT = 200.dp
private val HERO_CARD_WIDTH = 460.dp
private val WIDE_HERO_THRESHOLD = 600.dp

/**
 * The entries of [items] worth picking back up: read before and still unread, most recently read first. Empty while
 * searching, where the hero would push the results down.
 */
internal fun continueReadingItems(items: List<LibraryItem>, searchQuery: String?): List<LibraryItem> {
    if (!searchQuery.isNullOrEmpty()) return emptyList()
    return items
        .filter { it.libraryManga.lastRead != null && it.unreadCount > 0 }
        .sortedByDescending { it.libraryManga.lastRead }
        .take(MAX_CONTINUE_READING)
}

internal fun LazyGridScope.continueReadingHero(
    items: List<LibraryItem>,
    onClick: (LibraryManga) -> Unit,
    onClickContinueReading: (LibraryManga) -> Unit,
) {
    if (items.isEmpty()) return
    item(
        key = "library_continue_reading_hero",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "library_continue_reading_hero",
    ) {
        ContinueReadingHero(items, onClick, onClickContinueReading)
    }
}

internal fun LazyListScope.continueReadingHero(
    items: List<LibraryItem>,
    onClick: (LibraryManga) -> Unit,
    onClickContinueReading: (LibraryManga) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "library_continue_reading_hero", contentType = "library_continue_reading_hero") {
        ContinueReadingHero(
            items = items,
            onClick = onClick,
            onClickContinueReading = onClickContinueReading,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/**
 * A swipeable row of large glowing cards for the entries being read, each with its cover, a synopsis and a button
 * to resume.
 */
@Composable
private fun ContinueReadingHero(
    items: List<LibraryItem>,
    onClick: (LibraryManga) -> Unit,
    onClickContinueReading: (LibraryManga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { items.size }
    BoxWithConstraints(modifier = modifier.padding(bottom = 12.dp)) {
        // Wide screens show several cards side by side rather than one stretched across.
        val pageSize = if (maxWidth > WIDE_HERO_THRESHOLD) PageSize.Fixed(HERO_CARD_WIDTH) else PageSize.Fill
        val peek = items.size > 1 && pageSize == PageSize.Fill
        Column {
            Text(
                text = stringResource(MR.strings.library_continue_reading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
            )
            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                pageSize = pageSize,
                contentPadding = PaddingValues(end = if (peek) 24.dp else 0.dp),
                // Room for the glow, which the pager would clip
                modifier = Modifier.padding(vertical = 4.dp),
                key = { items[it].id },
            ) { page ->
                ContinueReadingCard(
                    item = items[page],
                    onClick = onClick,
                    onClickContinueReading = onClickContinueReading,
                )
            }
            if (items.size > 1) {
                PageDots(count = items.size, current = pagerState.currentPage)
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val selected = current == index
            val width by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                },
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(NeonDefaults.PillShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(
    item: LibraryItem,
    onClick: (LibraryManga) -> Unit,
    onClickContinueReading: (LibraryManga) -> Unit,
) {
    val manga = item.libraryManga.manga
    val shape = NeonDefaults.HeroShape
    val primary = MaterialTheme.colorScheme.primary
    val cover = MangaCoverModel(
        mangaId = manga.id,
        sourceId = manga.source,
        isMangaFavorite = manga.favorite,
        url = manga.thumbnailUrl,
        lastModified = manga.coverLastModified,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_CARD_HEIGHT)
            .neonGlow(shape, primary.copy(alpha = 0.7f), 14.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .neonOutline(shape, primary.copy(alpha = 0.8f))
            .clickable(role = Role.Button) { onClick(item.libraryManga) },
    ) {
        // The cover again as a dim backdrop
        AsyncImage(
            model = cover,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(24.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(
                data = cover,
                shape = NeonDefaults.CoverShape,
                modifier = Modifier
                    .fillMaxHeight()
                    .neonGlow(NeonDefaults.CoverShape, primary.copy(alpha = 0.6f), 8.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = manga.description?.trim()
                if (!description.isNullOrEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(MR.strings.library_unread_count, item.unreadCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = lerp(primary, Color.White, 0.25f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(NeonDefaults.PillShape)
                        .background(NeonDefaults.accentGradient)
                        .clickable(role = Role.Button) { onClickContinueReading(item.libraryManga) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.RoundedFilled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.action_resume),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
