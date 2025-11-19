package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData
@Composable
fun MangaReadTimeChart(
    readDurations: List<ReadDurationByManga>,
    onMangaClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxTime = readDurations.maxOfOrNull { it.totalTimeRead } ?: 1L

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        readDurations.forEach { item ->
            MangaReadTimeItem(
                mangaId = item.mangaId,
                title = item.title,
                timeRead = item.totalTimeRead,
                maxTime = maxTime,
                cover = item.cover,
                onMangaClick = onMangaClick,
            )
        }
    }
}

@Composable
private fun MangaReadTimeItem(
    mangaId: Long,
    title: String,
    timeRead: Long,
    maxTime: Long,
    cover: MangaCoverData,
    onMangaClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onMangaClick(mangaId) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Manga cover thumbnail
        MangaCover.Book(
            data = cover,
            modifier = Modifier.size(48.dp,  60.dp),
            contentDescription = title,
        )

        // Title and progress bar column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Progress bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                fraction = (timeRead.toFloat() / maxTime.toFloat()).coerceIn(0f, 1f)
                            )
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                // Time text
                Text(
                    text = formatDuration(timeRead),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.widthIn(min = 50.dp),
                )
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60000
    return when {
        minutes < 60 -> "${minutes}m"
        else -> {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes == 0L) "${hours}h"
            else "${hours}h ${remainingMinutes}m"
        }
    }
}
