package eu.kanade.presentation.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart.SmartSearchEngine
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.QueryStats
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Renders the merged, ranked smart-search results as a single vertical list ordered by
 * relevance, with a "did you mean" suggestion banner at the top when nothing matched.
 */
@Composable
fun SmartGlobalSearchContent(
    results: List<SmartSearchEngine.MergedResult>,
    suggestions: List<String>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            item(key = "suggestions") {
                DidYouMeanBanner(suggestions = suggestions)
            }
        }

        if (results.isEmpty() && suggestions.isEmpty()) {
            item {
                Text(
                    text = stringResource(MR.strings.no_results_found),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.padding.medium)
                        .padding(horizontal = MaterialTheme.padding.medium),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@LazyColumn
        }

        items(results.size, key = { "${results[it].primary.id}-${results[it].sourceId}" }) { index ->
            val result = results[index]
            SmartSearchRowItem(
                rank = index + 1,
                result = result,
                getManga = getManga,
                onClick = { onClickItem(result.primary) },
                onLongClick = { onLongClickItem(result.primary) },
            )
        }
    }
}

/**
 * A single ranked result row: rank badge, cover, title, source tag, and the number of
 * alternative sources this series was found on (so the user can pick another source).
 */
@Composable
private fun SmartSearchRowItem(
    rank: Int,
    result: SmartSearchEngine.MergedResult,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title by getManga(result.primary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.small, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankNumberBadge(rank = rank)

        Box(modifier = Modifier.width(64.dp)) {
            MangaComfortableGridItem(
                title = title.title,
                titleMaxLines = 3,
                coverData = title.asMangaCover(),
                coverBadgeStart = {
                    InLibraryBadge(enabled = title.favorite)
                },
                coverAlpha = if (title.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SourceTagLine(result = result)
        }
    }
}

@Composable
private fun SourceTagLine(result: SmartSearchEngine.MergedResult) {
    val alternativeCount = result.alternatives.size
    val label = if (alternativeCount > 0) {
        stringResource(MR.strings.smart_search_source_tag, alternativeCount + 1)
    } else {
        stringResource(MR.strings.smart_search_single_source)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RankNumberBadge(rank: Int) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DidYouMeanBanner(suggestions: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.QueryStats,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = stringResource(MR.strings.did_you_mean),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (suggestions.isNotEmpty()) {
                Text(
                    text = suggestions.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
