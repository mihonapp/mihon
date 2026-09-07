package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

data class OnlineMangaDetailUiState(
    val source: SourceDescriptor,
    val manga: SManga,
    val chapters: List<SChapter> = emptyList(),
    val inLibrary: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncingLibrary: Boolean = false,
    val errorMessage: String? = null,
)

@Composable
fun OnlineMangaDetailScreen(
    state: OnlineMangaDetailUiState,
    onBack: () -> Unit,
    onAddToLibrary: () -> Unit,
    onReadChapter: (SChapter) -> Unit,
    onRefresh: () -> Unit = {},
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("online-manga-detail-screen"),
    ) {
        // Navigation bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("detail-back-btn")) {
                Text(strings.mangaDetailBack)
            }
            Row {
                Button(
                    onClick = onAddToLibrary,
                    enabled = !state.isSyncingLibrary,
                    modifier = Modifier.testTag("add-to-library-btn"),
                ) {
                    if (state.isSyncingLibrary) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.inLibrary) strings.mangaDetailInLibrary else strings.mangaDetailAddToLibrary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (state.manga.url.startsWith("http")) {
                    OutlinedButton(
                        onClick = { mihon.desktop.platform.DesktopBrowserHelper.openInBrowser(state.manga.url) },
                        modifier = Modifier.testTag("detail-open-browser-btn"),
                    ) {
                        Text(strings.openInBrowser)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.testTag("detail-refresh-btn")) {
                    Text(strings.browseRefresh)
                }
            }
        }

        // Header info
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            // Thumbnail
            mihon.desktop.ui.common.MangaCover(
                thumbnailUrl = state.manga.thumbnailUrl,
                contentDescription = state.manga.title,
                modifier = Modifier.width(140.dp).height(200.dp),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.onlineDetailSource(state.source.name, state.source.lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.manga.author?.let {
                    Text(text = strings.onlineDetailAuthor(it), style = MaterialTheme.typography.bodySmall)
                }
                state.manga.artist?.let {
                    Text(text = strings.onlineDetailArtist(it), style = MaterialTheme.typography.bodySmall)
                }
                if (state.manga.genre.isNotEmpty()) {
                    Text(
                        text = strings.onlineDetailGenres(state.manga.genre.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                state.manga.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Chapters Section
        Text(
            text = strings.onlineDetailChapters(state.chapters.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("chapters-loading-indicator"))
            }
        } else if (state.chapters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    strings.mangaDetailNoChapters,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f).testTag("chapters-list")) {
                items(state.chapters, key = { it.url }) { chapter ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            vertical = 12.dp,
                            horizontal = 8.dp,
                        ).testTag("chapter-row-" + chapter.url),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chapter.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            chapter.scanlator?.let {
                                Text(
                                    text = strings.onlineDetailScanlator(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Button(
                            onClick = { onReadChapter(chapter) },
                            modifier = Modifier.testTag("read-chapter-btn-" + chapter.url),
                        ) {
                            Text(strings.updatesReadButton)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
