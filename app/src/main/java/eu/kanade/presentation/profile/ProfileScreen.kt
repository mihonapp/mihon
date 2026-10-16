package eu.kanade.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AfragaNeon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.NeonPill
import eu.kanade.presentation.components.SplitColorHeader
import eu.kanade.presentation.components.glassSurface
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Settings
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ProfileScreen(
    items: List<LibraryItem>,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenUpdates: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManga: (Long) -> Unit,
) {
    val unreadItems = items.filter { it.unreadCount > 0 }
    val readingItems = items.filter { it.libraryManga.hasStarted && it.unreadCount > 0 }
    val unreadCount = items.sumOf { it.unreadCount }

    Scaffold(
        topBar = {
            AppBar(title = stringResource(MR.strings.profile_title))
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                ProfileHero(
                    featured = items.firstOrNull(),
                    seriesCount = items.size,
                    unreadCount = unreadCount,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeonPill(
                        text = stringResource(MR.strings.profile_saved_count, items.size),
                        tone = AfragaNeon.Purple,
                        modifier = Modifier.weight(1f),
                    )
                    NeonPill(
                        text = stringResource(MR.strings.profile_reading_count, readingItems.size),
                        tone = AfragaNeon.Emerald,
                        modifier = Modifier.weight(1f),
                    )
                    NeonPill(
                        text = stringResource(MR.strings.profile_unread_count, unreadCount),
                        tone = AfragaNeon.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (readingItems.isNotEmpty()) {
                item {
                    MangaShelf(
                        titleFirst = stringResource(MR.strings.profile_continue_first),
                        titleSecond = stringResource(MR.strings.profile_continue_second),
                        items = readingItems,
                        onOpenManga = onOpenManga,
                    )
                }
            }
            if (unreadItems.isNotEmpty()) {
                item {
                    MangaShelf(
                        titleFirst = stringResource(MR.strings.profile_collection_first),
                        titleSecond = stringResource(MR.strings.profile_collection_second),
                        items = unreadItems,
                        onOpenManga = onOpenManga,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SplitColorHeader(
                        firstWord = stringResource(MR.strings.profile_activity_first),
                        secondWord = stringResource(MR.strings.profile_activity_second),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonPill(
                            text = stringResource(MR.strings.label_recent_updates),
                            tone = AfragaNeon.Amber,
                            icon = MaterialSymbols.Rounded.Refresh,
                            onClick = onOpenUpdates,
                        )
                        NeonPill(
                            text = stringResource(MR.strings.history),
                            tone = AfragaNeon.Cyan,
                            icon = MaterialSymbols.Rounded.Schedule,
                            onClick = onOpenHistory,
                        )
                        NeonPill(
                            text = stringResource(MR.strings.label_settings),
                            tone = AfragaNeon.Purple,
                            icon = MaterialSymbols.Rounded.Settings,
                            onClick = onOpenSettings,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(
    featured: LibraryItem?,
    seriesCount: Int,
    unreadCount: Long,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(286.dp)
            .clip(shape)
            .background(Color(0xFF0B0D14)),
    ) {
        if (featured != null) {
            AsyncImage(
                model = featured.libraryManga.manga.let { manga ->
                    MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    )
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp)
                    .alpha(0.28f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x260B0D14),
                        1f to Color(0xEE0B0D14),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
                .glassSurface(
                    shape = RoundedCornerShape(22.dp),
                    tint = Color(0x99191E30),
                    glow = Color(0xFF00B0FF),
                    cornerRadius = 22.dp,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(MR.strings.profile_local_label).uppercase(),
                color = Color(0xFF40C4FF),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = stringResource(MR.strings.profile_hero_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(MR.strings.profile_hero_subtitle, seriesCount, unreadCount),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MangaShelf(
    titleFirst: String,
    titleSecond: String,
    items: List<LibraryItem>,
    onOpenManga: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SplitColorHeader(
            firstWord = titleFirst,
            secondWord = titleSecond,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ProfileMangaPoster(item = item, onClick = { onOpenManga(item.id) })
            }
        }
    }
}

@Composable
private fun ProfileMangaPoster(
    item: LibraryItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 126.dp, height = 222.dp)
            .clickable(onClick = onClick)
            .glassSurface(
                shape = RoundedCornerShape(18.dp),
                tint = Color(0x99191E30),
                border = Color.White.copy(alpha = 0.12f),
                cornerRadius = 18.dp,
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = item.libraryManga.manga.let { manga ->
                MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                )
            },
            contentDescription = item.libraryManga.manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
        )
        Text(
            text = item.libraryManga.manga.title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(
            text = stringResource(MR.strings.profile_unread_item_count, item.unreadCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
