package eu.kanade.presentation.more.anilistwishlist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.anilist.model.AnilistWishlistEntry

@Composable
fun AnilistWishlistScreenContent(
    entries: List<AnilistWishlistEntry>,
    paddingValues: PaddingValues,
    onRefresh: () -> Unit,
    onFindInSource: (AnilistWishlistEntry) -> Unit,
) {
    LazyColumn(contentPadding = paddingValues) {
        item {
            Button(onClick = onRefresh) {
                Text("Refresh")
            }
        }

        if (entries.isEmpty()) {
            item {
                Text("Your AniList Plan to Read list is empty.")
            }
        }

        items(entries, key = { it.mediaId }) { entry ->
            ListItem(
                leadingContent = {
                    MangaCover.Book(
                        data = entry.coverUrl,
                        modifier = Modifier.height(72.dp),
                        contentDescription = entry.title,
                    )
                },
                headlineContent = {
                    Text(entry.title)
                },
                supportingContent = {
                    Text(
                        entry.totalChapters?.let { "Chapters: $it" }
                            ?: "Chapters: Unknown",
                    )
                },
                trailingContent = {
                    TextButton(onClick = { onFindInSource(entry) }) {
                        Text("Find in source")
                    }
                },
            )
        }
    }
}
