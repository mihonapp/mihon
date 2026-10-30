package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.components.MangaListItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Warning
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun SourceLibraryScreen(
    title: String,
    titles: List<Manga>,
    errorIds: Set<Long>,
    onClickManga: (Long) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (titles.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.source_no_library_titles,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            SourceLibraryList(
                titles = titles,
                errorIds = errorIds,
                contentPadding = contentPadding,
                onClickManga = onClickManga,
            )
        }
    }
}

@Composable
private fun SourceLibraryList(
    titles: List<Manga>,
    errorIds: Set<Long>,
    contentPadding: PaddingValues,
    onClickManga: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        items(
            items = titles,
            key = { it.id },
        ) { manga ->
            MangaListItem(
                title = manga.title,
                coverData = manga.asMangaCover(),
                onClick = { onClickManga(manga.id) },
                onLongClick = { onClickManga(manga.id) },
                badge = {
                    if (manga.id in errorIds) {
                        Badge(
                            imageVector = MaterialSymbols.RoundedFilled.Warning,
                            color = MaterialTheme.colorScheme.error,
                            iconColor = MaterialTheme.colorScheme.onError,
                        )
                    }
                },
            )
        }
    }
}
