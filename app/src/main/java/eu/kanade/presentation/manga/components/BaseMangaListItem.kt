package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.util.padding

@Composable
fun BaseMangaListItem(
    modifier: Modifier = Modifier,
    manga: Manga,
    onClickItem: () -> Unit = {},
    onClickCover: () -> Unit = onClickItem,
    cover: @Composable RowScope.() -> Unit = { defaultCover(manga, onClickCover) },
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit = { defaultContent(manga) },
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickItem)
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()
        content()
        actions()
    }
}

private val defaultCover: @Composable RowScope.(Manga, () -> Unit) -> Unit = { manga, onClick ->
    MangaCover.Square(
        modifier = Modifier
            .padding(vertical = MaterialTheme.padding.small)
            .fillMaxHeight(),
        data = manga,
        onClick = onClick,
    )
}

private val defaultContent: @Composable RowScope.(Manga) -> Unit = {
    Box(modifier = Modifier.weight(1f)) {
        Text(
            text = it.title,
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
