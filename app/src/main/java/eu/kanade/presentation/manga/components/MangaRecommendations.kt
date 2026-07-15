package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.presentation.core.components.material.padding

@Composable
fun MangaRecommendationRow(
    title: String,
    manga: List<Manga>,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (manga.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.padding.small),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        )

        LazyRow(
            contentPadding = PaddingValues(MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            items(
                items = manga,
                key = { "${it.source}:${it.url}" },
                contentType = { RecommendationRowContentType.MANGA_CARD },
            ) { item ->
                Box(modifier = Modifier.width(96.dp)) {
                    MangaComfortableGridItem(
                        title = item.title,
                        titleMaxLines = 3,
                        coverData = item.asMangaCover(),
                        coverBadgeStart = {
                            InLibraryBadge(enabled = item.favorite)
                        },
                        coverAlpha = if (item.favorite) {
                            CommonMangaItemDefaults.BrowseFavoriteCoverAlpha
                        } else {
                            1f
                        },
                        onClick = { onMangaClick(item) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

private enum class RecommendationRowContentType {
    MANGA_CARD,
}
