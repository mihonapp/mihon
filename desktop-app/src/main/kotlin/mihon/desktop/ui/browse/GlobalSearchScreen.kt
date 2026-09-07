package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.ui.common.MangaCover
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

data class GlobalSearchSourceResult(
    val source: SourceDescriptor,
    val isLoading: Boolean = false,
    val mangas: List<SManga> = emptyList(),
    val errorMessage: String? = null,
)

@Composable
fun GlobalSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    isSearching: Boolean,
    sourceResults: List<GlobalSearchSourceResult>,
    onMangaSelected: (SourceDescriptor, SManga) -> Unit,
    onViewSource: (SourceDescriptor) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("global-search-screen"),
    ) {
        // Top Search Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("global-search-back-btn")) {
                Text(strings.mangaDetailBack)
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).testTag("global-search-input"),
                placeholder = { Text(strings.browseSearchPlaceholder) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching,
                modifier = Modifier.testTag("global-search-submit-btn"),
            ) {
                Text(strings.browseSearchButton)
            }
        }

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }

        if (sourceResults.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isBlank()) {
                        strings.globalSearchEnterQuery
                    } else {
                        strings.globalSearchNoSources
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("global-search-results-list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(sourceResults, key = { it.source.id }) { item ->
                    Column(modifier = Modifier.fillMaxWidth().testTag("source-result-${item.source.id}")) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.source.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.source.lang.uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                if (item.isLoading) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(16.dp).height(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            TextButton(onClick = { onViewSource(item.source) }) {
                                Text(strings.globalSearchViewAll)
                            }
                        }

                        item.errorMessage?.let { error ->
                            Text(
                                text = strings.globalSearchError(error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        if (!item.isLoading && item.mangas.isEmpty() && item.errorMessage == null) {
                            Text(
                                text = strings.globalSearchNoResults,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else if (item.mangas.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(item.mangas, key = { it.url }) { manga ->
                                    Card(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clickable { onMangaSelected(item.source, manga) }
                                            .testTag("global-manga-${manga.url.hashCode()}"),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    ) {
                                        Column {
                                            MangaCover(
                                                thumbnailUrl = manga.thumbnailUrl,
                                                contentDescription = manga.title,
                                                modifier = Modifier.fillMaxWidth().height(175.dp),
                                            )
                                            Text(
                                                text = manga.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
