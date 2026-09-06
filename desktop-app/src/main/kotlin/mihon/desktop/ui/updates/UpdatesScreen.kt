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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.updates.LibraryUpdateResult
import mihon.desktop.updates.UpdatedChapterItem

const val UPDATES_SCREEN_TEST_TAG = "updates_screen"
const val UPDATES_CHECK_NOW_BUTTON_TEST_TAG = "updates_check_now"
const val UPDATES_ITEM_TEST_TAG_PREFIX = "updates_item_"

@Composable
fun UpdatesScreen(
    updatedChapters: List<UpdatedChapterItem>,
    isUpdating: Boolean,
    lastResult: LibraryUpdateResult?,
    onCheckForUpdates: () -> Unit,
    onReadChapter: (chapterId: Long) -> Unit,
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
                    Text(strings.updatesCheckButton)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (updatedChapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.updatesEmptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.mangaTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = item.chapterName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            TextButton(onClick = { onReadChapter(item.chapterId) }) {
                                Text(strings.updatesReadButton)
                            }
                        }
                    }
                }
            }
        }
    }
}
