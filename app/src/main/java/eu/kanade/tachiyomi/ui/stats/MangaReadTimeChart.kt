package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.StatsCoverStyle
import eu.kanade.domain.ui.model.StatsProgressBarStyle
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MangaReadTimeChart(
    readDurations: List<ReadDurationByManga>,
    progressBarStyle: StatsProgressBarStyle,
    onMangaClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val coverStyle by uiPreferences.statsScreenCoverStyle().collectAsState()

    val maxTime = readDurations.maxOfOrNull { it.totalTimeRead } ?: 1L
    val totalTime = readDurations.sumOf { it.totalTimeRead }

    val scaleBase = when (progressBarStyle) {
        StatsProgressBarStyle.RELATIVE_TO_MAX -> maxTime
        StatsProgressBarStyle.RELATIVE_TO_TOTAL -> totalTime
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            if (coverStyle == StatsCoverStyle.SQUARE) {
                MaterialTheme.padding.extraSmall
            } else {
                MaterialTheme.padding.medium
            }
        ),
    ) {
        readDurations.forEach { item ->
            MangaReadTimeItem(
                mangaId = item.mangaId,
                title = item.title,
                timeRead = item.totalTimeRead,
                scaleBase = scaleBase,
                cover = item.cover,
                coverStyle = coverStyle,
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
    scaleBase: Long,
    cover: MangaCoverData,
    coverStyle: StatsCoverStyle,
    onMangaClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onMangaClick(mangaId) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Manga cover thumbnail
        when (coverStyle) {
            StatsCoverStyle.SQUARE -> {
                MangaCover.Square(
                    data = cover,
                    modifier = Modifier.size(48.dp),
                    contentDescription = title,
                )
            }
            StatsCoverStyle.BOOK -> {
                MangaCover.Book(
                    data = cover,
                    modifier = Modifier.size(width = 40.dp, height = 60.dp),
                    contentDescription = title,
                )
            }
        }

        // Title and progress bar column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Progress bar
            LinearProgressIndicator(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .fillMaxWidth()
                    .height(12.dp),
                progress = { (timeRead.toFloat() / scaleBase.toFloat()).coerceIn(0f, 1f) },
            )

            // Time text
            Text(
                text = formatDuration(timeRead),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
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
