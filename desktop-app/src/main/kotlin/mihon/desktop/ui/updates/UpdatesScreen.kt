package mihon.desktop.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.updates.LibraryUpdateResult
import mihon.desktop.updates.UpdatedChapterItem

const val UPDATES_SCREEN_TEST_TAG = "updates_screen"
const val UPDATES_CHECK_NOW_BUTTON_TEST_TAG = "updates_check_now"
const val UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG = "updates_open_upcoming"
const val UPDATES_ITEM_TEST_TAG_PREFIX = "updates_item_"

@Composable
fun UpdatesScreen(
    updatedChapters: List<UpdatedChapterItem>,
    isUpdating: Boolean,
    lastResult: LibraryUpdateResult?,
    onCheckForUpdates: () -> Unit,
    onReadChapter: (chapterId: Long) -> Unit,
    onOpenUpcoming: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(UPDATES_SCREEN_TEST_TAG)
            .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = strings.updatesTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = when {
                    isUpdating -> strings.updatesChecking
                    lastResult != null -> {
                        if (lastResult.newChaptersFound > 0) {
                            "Found ${lastResult.newChaptersFound} new chapters across ${lastResult.mangaWithNewChapters} manga"
                        } else {
                            strings.updatesEmptySubtitle
                        }
                    }
                    else -> strings.updatesEmptySubtitle
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onOpenUpcoming,
                    modifier = Modifier.testTag(UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upcoming")
                }

                Button(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.testTag(UPDATES_CHECK_NOW_BUTTON_TEST_TAG),
                    enabled = !isUpdating,
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.updatesChecking)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.updatesCheckButton)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (updatedChapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = strings.updatesEmptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.updatesEmptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(updatedChapters, key = { it.chapterId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UPDATES_ITEM_TEST_TAG_PREFIX + item.chapterId),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MangaCover(
                                thumbnailUrl = item.mangaThumbnailUrl,
                                mangaId = item.mangaId,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = item.mangaTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.chapterName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.dateFetch > 0L) {
                                    val timeStr = remember(item.dateFetch) {
                                        try {
                                            java.time.Instant.ofEpochMilli(item.dateFetch)
                                                .atZone(java.time.ZoneId.systemDefault())
                                                .format(
                                                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                                                )
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    if (timeStr != null) {
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(onClick = { onReadChapter(item.chapterId) }) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.updatesReadButton)
                            }
                        }
                    }
                }
            }
        }
    }
}
