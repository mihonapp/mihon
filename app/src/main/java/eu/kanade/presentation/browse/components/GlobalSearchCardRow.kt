package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.onPositive
import tachiyomi.presentation.core.theme.positive

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
    getChapterCountDelta: @Composable (Manga) -> Int? = { null },
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(titles) {
            val title by getManga(it)
            MangaItem(
                title = title.title,
                cover = title.asMangaCover(),
                isFavorite = title.favorite,
                chapterCountDelta = getChapterCountDelta(title),
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
            )
        }
    }
}

@Composable
private fun MangaItem(
    title: String,
    cover: MangaCover,
    isFavorite: Boolean,
    chapterCountDelta: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val coverBadgeEnd: (@Composable RowScope.() -> Unit)? = if (chapterCountDelta != null) {
        { ChapterCountDeltaBadge(delta = chapterCountDelta) }
    } else {
        null
    }
    Box(modifier = Modifier.width(96.dp)) {
        MangaComfortableGridItem(
            title = title,
            titleMaxLines = 3,
            coverData = cover,
            coverBadgeStart = {
                InLibraryBadge(enabled = isFavorite)
            },
            coverBadgeEnd = coverBadgeEnd,
            coverAlpha = if (isFavorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

internal fun chapterCountDeltaLabel(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> delta.toString()
    else -> "="
}

@Composable
private fun ChapterCountDeltaBadge(delta: Int) {
    val text = chapterCountDeltaLabel(delta)
    val backgroundColor = when {
        delta > 0 -> MaterialTheme.colorScheme.positive
        delta < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    val textColor = when {
        delta > 0 -> MaterialTheme.colorScheme.onPositive
        delta < 0 -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSecondary
    }
    Badge(text = text, color = backgroundColor, textColor = textColor)
}

@Composable
private fun EmptyResultItem() {
    Text(
        text = stringResource(MR.strings.no_results_found),
        modifier = Modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
    )
}
