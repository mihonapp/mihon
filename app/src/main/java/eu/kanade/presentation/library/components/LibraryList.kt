package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.MangaCover.Square
import eu.kanade.presentation.components.TextButton
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.selectedBackground
import eu.kanade.presentation.util.verticalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView.Companion.bottomNavPadding

@Composable
fun LibraryList(
    items: List<LibraryItem>,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomNavPadding + contentPadding,
    ) {
        item {
            if (searchQuery.isNullOrEmpty().not()) {
                TextButton(onClick = onGlobalSearchClicked) {
                    Text(
                        text = stringResource(R.string.action_global_search_query, searchQuery!!),
                        modifier = Modifier.zIndex(99f),
                    )
                }
            }
        }

        items(
            items = items,
            contentType = { "library_list_item" },
        ) { libraryItem ->
            LibraryListItem(
                item = libraryItem,
                isSelected = libraryItem.libraryManga in selection,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
fun LibraryListItem(
    item: LibraryItem,
    isSelected: Boolean,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
) {
    val libraryManga = item.libraryManga
    val manga = libraryManga.manga
    MangaListItem(
        modifier = Modifier.selectedBackground(isSelected),
        title = manga.title,
        cover = MangaCover(
            manga.id,
            manga.source,
            manga.favorite,
            manga.thumbnailUrl,
            manga.coverLastModified,
        ),
        onClick = { onClick(libraryManga) },
        onLongClick = { onLongClick(libraryManga) },
    ) {
        if (item.downloadCount > 0) {
            Badge(
                text = "${item.downloadCount}",
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        if (item.unreadCount > 0) {
            Badge(text = "${item.unreadCount}")
        }
        if (item.isLocal) {
            Badge(
                text = stringResource(R.string.local_source_badge),
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        if (item.isLocal.not() && item.sourceLanguage.isNotEmpty()) {
            Badge(
                text = item.sourceLanguage,
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

@Composable
fun MangaListItem(
    modifier: Modifier = Modifier,
    title: String,
    cover: MangaCover,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    badges: @Composable RowScope.() -> Unit,
) {
    MangaListItem(
        modifier = modifier,
        coverContent = {
            Square(
                modifier = Modifier
                    .padding(vertical = verticalPadding)
                    .fillMaxHeight(),
                data = cover,
            )
        },
        badges = badges,
        onClick = onClick,
        onLongClick = onLongClick,
        content = {
            MangaListItemContent(title)
        },
    )
}

@Composable
fun MangaListItem(
    modifier: Modifier = Modifier,
    coverContent: @Composable RowScope.() -> Unit,
    badges: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coverContent()
        content()
        BadgeGroup(content = badges)
    }
}

@Composable
fun RowScope.MangaListItemContent(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}
