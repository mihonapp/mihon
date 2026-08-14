package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.tachiyomi.data.recommendation.RecommendationCard
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.padding

@Composable
internal fun MangaRecommendationRow(
    title: String,
    recommendations: List<RecommendationCard>,
    onRecommendationClick: (RecommendationCard) -> Unit,
) {
    if (recommendations.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        ListGroupHeader(text = title)
        LazyRow(
            contentPadding = PaddingValues(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                bottom = MaterialTheme.padding.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            items(
                items = recommendations,
                key = { it.identity.exposureKey },
                contentType = { RecommendationCard::class },
            ) { recommendation ->
                RecommendationItem(
                    recommendation = recommendation,
                    onClick = { onRecommendationClick(recommendation) },
                )
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    recommendation: RecommendationCard,
    onClick: () -> Unit,
) {
    val cover = remember(recommendation) {
        MangaCover(
            mangaId = recommendation.localId ?: recommendation.identity.exposureKey.hashCode().toLong(),
            sourceId = recommendation.sourceId,
            isMangaFavorite = recommendation.favorite,
            url = recommendation.manga.thumbnail_url,
            lastModified = 0L,
        )
    }

    Box(modifier = Modifier.width(96.dp)) {
        MangaComfortableGridItem(
            title = recommendation.manga.title,
            titleMaxLines = 3,
            coverData = cover,
            coverBadgeStart = { InLibraryBadge(enabled = recommendation.favorite) },
            coverAlpha = if (recommendation.favorite) {
                CommonMangaItemDefaults.BrowseFavoriteCoverAlpha
            } else {
                1f
            },
            onClick = onClick,
            onLongClick = {},
        )
    }
}
