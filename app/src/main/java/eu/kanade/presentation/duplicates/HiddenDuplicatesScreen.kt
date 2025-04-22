package eu.kanade.presentation.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.duplicates.components.DuplicateMangaListItem
import eu.kanade.presentation.duplicates.components.ManageDuplicateAction
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
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }

    ScrollbarLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(verticalListPadding),
    ) {
        items(
            items = hiddenDuplicatesMap.toList(),
        ) { duplicatePair ->
            val height =
                getMaximumMangaCardHeight(
                    duplicatePair.second + duplicatePair.first,
                    hiddenDuplicatesCardWidth,
                    actions = true,
                )

            Row(
                modifier = Modifier
                    .height(height)
                    .padding(start = MaterialTheme.padding.small),
            ) {
                Column {
                    DuplicateMangaListItem(
                        duplicate = duplicatePair.first,
                        getSource = { sourceManager.getOrStub(duplicatePair.first.manga.source) },
                        cardWidth = hiddenDuplicatesCardWidth,
                        onClick = { onOpenManga(duplicatePair.first.manga) },
                        onDismissRequest = onDismissRequest,
                        onLongClick = { onOpenManga(duplicatePair.first.manga) },
                        actions = listOf(
                            ManageDuplicateAction(
                                icon = Icons.Outlined.Visibility,
                                onClick = { onUnhideGroupClicked(duplicatePair.first, duplicatePair.second) },
                            ),
                        ),
                    )
                }
                VerticalDivider(
                    modifier = Modifier.padding(horizontalListPadding),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(horizontalListPadding),
                ) {
                    items(
                        items = duplicatePair.second,
                    ) { duplicate ->
                        DuplicateMangaListItem(
                            duplicate = duplicate,
                            getSource = { sourceManager.getOrStub(duplicate.manga.source) },
                            cardWidth = hiddenDuplicatesCardWidth,
                            onClick = { onOpenManga(duplicate.manga) },
                            onDismissRequest = onDismissRequest,
                            onLongClick = { onOpenManga(duplicate.manga) },
                            actions = listOf(
                                ManageDuplicateAction(
                                    icon = Icons.Outlined.Visibility,
                                    onClick = { onUnhideSingleClicked(duplicatePair.first, duplicate) },
                                ),
                            ),
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = verticalListPadding),
            )
        }
    }
}

private val hiddenDuplicatesCardWidth = 120.dp
private val horizontalListPadding = MaterialTheme.padding.extraSmall
private val verticalListPadding = MaterialTheme.padding.extraSmall
