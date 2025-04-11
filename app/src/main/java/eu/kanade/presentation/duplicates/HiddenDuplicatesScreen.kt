package eu.kanade.presentation.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.duplicates.components.DuplicateMangaListItem
import eu.kanade.presentation.duplicates.components.getMaximumMangaCardHeight
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.padding
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun HiddenDuplicatesContent(
    hiddenDuplicatesMap: Map<MangaWithChapterCount, List<MangaWithChapterCount>>,
    paddingValues: PaddingValues,
    lazyListState: LazyListState,
    onOpenManga: (Manga) -> Unit,
    onDismissRequest: () -> Unit,
    onUnhideSingleClicked: (MangaWithChapterCount, MangaWithChapterCount) -> Unit,
    onUnhideGroupClicked: (MangaWithChapterCount, List<MangaWithChapterCount>) -> Unit,
    loading: Boolean,
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val horizontalPadding = PaddingValues(horizontal = TabbedDialogPaddings.Horizontal)

    ScrollbarLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = hiddenDuplicatesMap.toList(),
        ) { duplicatePair ->
            val height = getMaximumMangaCardHeight(duplicatePair.second + duplicatePair.first)

            Row (modifier = Modifier.height(height)) {
                Column (
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.small)
                ) {
                    DuplicateMangaListItem(
                        duplicate = duplicatePair.first,
                        getSource = { sourceManager.getOrStub(duplicatePair.first.manga.source) },
                        onClick = { onOpenManga(duplicatePair.first.manga) },
                        onDismissRequest = onDismissRequest,
                        onLongClick = { onOpenManga(duplicatePair.first.manga) },
                        onToggleFavoriteClicked = {  },
                        onHideDuplicateClicked = {  },
                    )
                }
                VerticalDivider()
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    contentPadding = horizontalPadding,
                ) {
                    items(
                        items = duplicatePair.second,
                    ) { duplicate ->
                        DuplicateMangaListItem(
                            duplicate = duplicate,
                            getSource = { sourceManager.getOrStub(duplicate.manga.source) },
                            onClick = { onOpenManga(duplicate.manga) },
                            onDismissRequest = onDismissRequest,
                            onLongClick = { onOpenManga(duplicate.manga) },
                            onToggleFavoriteClicked = {  },
                            onHideDuplicateClicked = {  },
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontalPadding)
                    .padding(top = MaterialTheme.padding.small),
            )
        }
        if (loading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 100.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
