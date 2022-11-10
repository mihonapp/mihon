package eu.kanade.presentation.manga

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.VerticalDivider
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import java.text.DateFormat

private const val UnsetStatusTextAlpha = 0.5F

@Composable
fun TrackInfoDialogHome(
    trackItems: List<TrackItem>,
    dateFormat: DateFormat,
    contentPadding: PaddingValues = PaddingValues(),
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onRemoved: (TrackItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        trackItems.forEach { item ->
            if (item.track != null) {
                val supportsScoring = item.service.getScoreList().isNotEmpty()
                val supportsReadingDates = item.service.supportsReadingDates
                TrackInfoItem(
                    title = item.track.title,
                    logoRes = item.service.getLogo(),
                    logoColor = item.service.getLogoColor(),
                    status = item.service.getStatus(item.track.status),
                    onStatusClick = { onStatusClick(item) },
                    chapters = "${item.track.last_chapter_read.toInt()}".let {
                        val totalChapters = item.track.total_chapters
                        if (totalChapters > 0) {
                            // Add known total chapter count
                            "$it / $totalChapters"
                        } else {
                            it
                        }
                    },
                    onChaptersClick = { onChapterClick(item) },
                    score = item.service.displayScore(item.track)
                        .takeIf { supportsScoring && item.track.score != 0F },
                    onScoreClick = { onScoreClick(item) }
                        .takeIf { supportsScoring },
                    startDate = remember(item.track.started_reading_date) { dateFormat.format(item.track.started_reading_date) }
                        .takeIf { supportsReadingDates && item.track.started_reading_date != 0L },
                    onStartDateClick = { onStartDateEdit(item) } // TODO
                        .takeIf { supportsReadingDates },
                    endDate = dateFormat.format(item.track.finished_reading_date)
                        .takeIf { supportsReadingDates && item.track.finished_reading_date != 0L },
                    onEndDateClick = { onEndDateEdit(item) }
                        .takeIf { supportsReadingDates },
                    onNewSearch = { onNewSearch(item) },
                    onOpenInBrowser = { onOpenInBrowser(item) },
                    onRemoved = { onRemoved(item) },
                )
            } else {
                TrackInfoItemEmpty(
                    logoRes = item.service.getLogo(),
                    logoColor = item.service.getLogoColor(),
                    onNewSearch = { onNewSearch(item) },
                )
            }
        }
    }
}

@Composable
private fun TrackInfoItem(
    title: String,
    @DrawableRes logoRes: Int,
    @ColorInt logoColor: Int,
    status: String,
    onStatusClick: () -> Unit,
    chapters: String,
    onChaptersClick: () -> Unit,
    score: String?,
    onScoreClick: (() -> Unit)?,
    startDate: String?,
    onStartDateClick: (() -> Unit)?,
    endDate: String?,
    onEndDateClick: (() -> Unit)?,
    onNewSearch: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRemoved: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenInBrowser)
                    .size(48.dp)
                    .background(color = Color(logoColor))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f)
                    .clickable(onClick = onNewSearch)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            VerticalDivider()
            TrackInfoItemMenu(
                onOpenInBrowser = onOpenInBrowser,
                onRemoved = onRemoved,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Column {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackDetailsItem(
                        modifier = Modifier.weight(1f),
                        text = status,
                        onClick = onStatusClick,
                    )
                    VerticalDivider()
                    TrackDetailsItem(
                        modifier = Modifier.weight(1f),
                        text = chapters,
                        onClick = onChaptersClick,
                    )
                    if (onScoreClick != null) {
                        VerticalDivider()
                        TrackDetailsItem(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (score == null) UnsetStatusTextAlpha else 1f),
                            text = score ?: stringResource(id = R.string.score),
                            onClick = onScoreClick,
                        )
                    }
                }

                if (onStartDateClick != null && onEndDateClick != null) {
                    Divider()
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        TrackDetailsItem(
                            modifier = Modifier
                                .weight(1F)
                                .alpha(if (startDate == null) UnsetStatusTextAlpha else 1f),
                            text = startDate ?: stringResource(id = R.string.track_started_reading_date),
                            onClick = onStartDateClick,
                        )
                        VerticalDivider()
                        TrackDetailsItem(
                            modifier = Modifier
                                .weight(1F)
                                .alpha(if (endDate == null) UnsetStatusTextAlpha else 1f),
                            text = endDate ?: stringResource(id = R.string.track_finished_reading_date),
                            onClick = onEndDateClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackDetailsItem(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrackInfoItemEmpty(
    @DrawableRes logoRes: Int,
    @ColorInt logoColor: Int,
    onNewSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .size(48.dp)
                .background(color = Color(logoColor))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = null,
            )
        }
        TextButton(
            onClick = onNewSearch,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(text = stringResource(id = R.string.add_tracking))
        }
    }
}

@Composable
private fun TrackInfoItemMenu(
    onOpenInBrowser: () -> Unit,
    onRemoved: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(id = R.string.label_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_in_browser)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null)
                },
                onClick = {
                    onOpenInBrowser()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                },
                onClick = {
                    onRemoved()
                    expanded = false
                },
            )
        }
    }
}
