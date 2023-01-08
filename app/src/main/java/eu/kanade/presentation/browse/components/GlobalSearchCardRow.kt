package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.asMangaCover
import eu.kanade.presentation.util.padding

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.tiny),
    ) {
        items(titles) {
            val title by getManga(it)
            GlobalSearchCard(
                title = title.title,
                cover = title.asMangaCover(),
                isFavorite = title.favorite,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
            )
        }
    }
}
