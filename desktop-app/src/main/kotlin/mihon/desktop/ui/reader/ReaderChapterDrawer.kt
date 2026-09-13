package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings

/**
 * Material 3 chapter drawer for in-reader chapter browsing, search, and navigation.
 * Mimics Mihon Android's in-reader chapter list bottom sheet / navigation drawer.
 */
@Composable
fun ReaderChapterDrawer(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    chapters: List<ReaderChapterTransitionChapter>,
    currentChapterId: Long?,
    onSelectChapter: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var isAscending by remember { mutableStateOf(false) }

    val filteredChapters = remember(chapters, searchQuery, isAscending) {
        val list = if (searchQuery.isBlank()) {
            chapters
        } else {
            chapters.filter { chapter ->
                chapter.title.contains(searchQuery, ignoreCase = true) ||
                    (chapter.scanlator?.contains(searchQuery, ignoreCase = true) == true) ||
                    (chapter.chapterNumber != null && chapter.chapterNumber.toString().contains(searchQuery))
            }
        }
        if (isAscending) {
            list.sortedBy { it.chapterNumber ?: 0.0 }
        } else {
            list.sortedByDescending { it.chapterNumber ?: 0.0 }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(isOpen, currentChapterId) {
        if (isOpen && currentChapterId != null) {
            val targetIdx = filteredChapters.indexOfFirst { it.id == currentChapterId }
            if (targetIdx >= 0) {
                listState.scrollToItem((targetIdx - 2).coerceAtLeast(0))
            }
        }
    }

    if (isOpen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismissRequest)
                .testTag("reader-chapter-drawer-overlay"),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(360.dp)
                    .clickable(enabled = false, onClick = {})
                    .testTag("reader-chapter-drawer-content"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("reader-drawer-title"),
                            )
                            Text(
                                text = "${chapters.size} ${strings.readerChapterList.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { isAscending = !isAscending },
                            modifier = Modifier.testTag("reader-drawer-sort"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort order",
                                tint = if (isAscending) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.testTag("reader-drawer-close"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                            )
                        }
                    }

                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("reader-drawer-search"),
                        placeholder = {
                            Text(strings.readerChapterDrawerSearch, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        ),
                    )

                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))

                    // Chapters list
                    if (filteredChapters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                                .testTag("reader-drawer-empty"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = strings.readerChapterDrawerNoResults,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("reader-drawer-list"),
                        ) {
                            items(filteredChapters, key = { it.id ?: it.title }) { chapter ->
                                val isCurrent = chapter.id != null && chapter.id == currentChapterId
                                val itemBg = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    Color.Transparent
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(itemBg)
                                        .clickable {
                                            chapter.id?.let {
                                                onSelectChapter(it)
                                                onDismissRequest()
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("reader-drawer-item-${chapter.id ?: chapter.title}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                                chapter.read -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!chapter.scanlator.isNullOrBlank()) {
                                            Text(
                                                text = chapter.scanlator,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    if (chapter.downloaded) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Downloaded",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    if (isCurrent) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ) {
                                            Text(
                                                text = strings.readerCurrentChapter,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
