package mihon.desktop.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.stats.DesktopStatsData
import java.util.Locale

const val STATS_SCREEN_TEST_TAG = "stats-screen"

@Composable
fun StatsScreen(
    data: DesktopStatsData,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    if (data.isLoading) {
        Box(
            modifier = modifier.fillMaxSize().testTag(STATS_SCREEN_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(STATS_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header with title and refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.statsTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.testTag("stats-refresh-button"),
            ) {
                Text(strings.statsRefresh)
            }
        }

        // 1. Overview Section
        Text(
            text = strings.statsOverview,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OverviewCard(
                title = data.overview.libraryMangaCount.toString(),
                subtitle = strings.statsTotalManga,
                modifier = Modifier.weight(1f),
            )
            OverviewCard(
                title = data.overview.formattedReadDuration,
                subtitle = strings.statsReadDuration,
                modifier = Modifier.weight(1f),
            )
            OverviewCard(
                title = data.overview.completedMangaCount.toString(),
                subtitle = strings.statsCompletedManga,
                modifier = Modifier.weight(1f),
            )
            Card(modifier = Modifier.weight(1.2f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${data.overview.readChapterCount} / ${data.overview.totalChapterCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.statsChapters,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { (data.overview.readPercentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.1f%% %s",
                            data.overview.readPercentage,
                            strings.statsReadPercentage,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // 2. Reading Status & Progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.statsStatuses,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    StatRow(strings.statsStatusOngoing, data.statuses.ongoingCount)
                    StatRow(strings.statsStatusCompleted, data.statuses.completedCount)
                    StatRow(strings.statsStatusHiatus, data.statuses.onHiatusCount)
                    StatRow(strings.statsStatusCancelled, data.statuses.cancelledCount)
                    StatRow(strings.statsStatusUnknown, data.statuses.unknownCount)
                }
            }

            Card(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.statsReadingProgress,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    StatRow(strings.statsProgressUnread, data.progress.unreadCount)
                    StatRow(strings.statsProgressInProgress, data.progress.inProgressCount)
                    StatRow(strings.statsProgressFinished, data.progress.finishedCount)
                }
            }
        }

        // 3. Top Genres Preference
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = strings.statsTopGenres,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (data.topGenres.isEmpty()) {
                    Text(
                        text = strings.statsNoGenres,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    data.topGenres.forEach { item ->
                        GenreBarItem(item.genre, item.count, item.percentage)
                    }
                }
            }
        }

        // 4. Categories & Tracking
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.statsCategories,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (data.categories.isEmpty()) {
                        Text(
                            text = strings.statsNoCategories,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        data.categories.forEach { cat ->
                            StatRow(cat.categoryName, cat.mangaCount)
                        }
                    }
                }
            }

            Card(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.statsTrackers,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    StatRow(strings.statsTrackedTitles, data.tracking.trackedMangaCount)
                    val scoreText = if (data.tracking.meanScore > 0.0) {
                        String.format(Locale.US, "%.1f ★", data.tracking.meanScore)
                    } else {
                        "-"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.statsMeanScore,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    StatRow(strings.statsActiveTrackers, data.tracking.trackerCount)
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GenreBarItem(genre: String, count: Int, percentage: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "$count (${String.format(Locale.US, "%.1f%%", percentage)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}
