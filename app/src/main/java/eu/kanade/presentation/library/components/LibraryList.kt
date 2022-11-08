package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.zIndex
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.MangaListItem
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun LibraryList(
    items: List<LibraryItem>,
    showDownloadBadges: Boolean,
    showUnreadBadges: Boolean,
    showLocalBadges: Boolean,
    showLanguageBadges: Boolean,
    showContinueReadingButton: Boolean,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: (LibraryManga) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (searchQuery.isNullOrEmpty().not()) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGlobalSearchClicked,
                ) {
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
            val manga = libraryItem.libraryManga.manga
            MangaListItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id },
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(enabled = showDownloadBadges, item = libraryItem)
                    UnreadBadge(enabled = showUnreadBadges, item = libraryItem)
                    LanguageBadge(showLanguage = showLanguageBadges, showLocal = showLocalBadges, item = libraryItem)
                },
                showContinueReadingButton = showContinueReadingButton,
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueReading = { onClickContinueReading(libraryItem.libraryManga) },
            )
        }
    }
}
