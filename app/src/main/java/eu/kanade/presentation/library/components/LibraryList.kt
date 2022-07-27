package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.TextButton
import eu.kanade.presentation.util.bottomNavPaddingValues
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.selectedBackground
import eu.kanade.presentation.util.verticalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryList(
    items: List<LibraryItem>,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = bottomNavPaddingValues,
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
            key = {
                it.manga.id!!
            },
        ) { libraryItem ->
            LibraryListItem(
                item = libraryItem,
                isSelected = libraryItem.manga in selection,
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
    val manga = item.manga
    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .height(56.dp)
            .combinedClickable(
                onClick = { onClick(manga) },
                onLongClick = { onLongClick(manga) },
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        eu.kanade.presentation.components.MangaCover.Square(
            modifier = Modifier
                .padding(vertical = verticalPadding)
                .fillMaxHeight(),
            data = MangaCover(
                manga.id!!,
                manga.source,
                manga.favorite,
                manga.thumbnail_url,
                manga.cover_last_modified,
            ),
        )
        Text(
            text = manga.title,
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .weight(1f),
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
        )
        BadgeGroup {
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
}
