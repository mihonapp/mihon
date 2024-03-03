package eu.kanade.presentation.track

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerChipElement
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.DateTimeFormatter

private const val UnsetStatusTextAlpha = 0.5F

@Composable
fun TrackInfoDialogHome(
    trackItems: List<TrackItem>,
    dateFormat: DateTimeFormatter,
    webUrlProvider: () -> List<String>?,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onNewIdSearch: (TrackerChipElement) -> Unit,
    onNewChipSearch: (TrackerChipElement) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onOpenChipElementInBrowser: (TrackerChipElement) -> Unit,
    onRemoved: (TrackItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TrackerAndUrlRow(
            trackItems,
            webUrlProvider,
            onNewChipSearch,
            onNewIdSearch,
            onOpenChipElementInBrowser,
        )
        trackItems.forEach { item ->
            if (item.track != null) {
                val supportsScoring = item.tracker.getScoreList().isNotEmpty()
                val supportsReadingDates = item.tracker.supportsReadingDates
                TrackInfoItem(
                    title = item.track.title,
                    tracker = item.tracker,
                    status = item.tracker.getStatus(item.track.status),
                    onStatusClick = { onStatusClick(item) },
                    chapters = "${item.track.lastChapterRead.toInt()}".let {
                        val totalChapters = item.track.totalChapters
                        if (totalChapters > 0) {
                            // Add known total chapter count
                            "$it / $totalChapters"
                        } else {
                            it
                        }
                    },
                    onChaptersClick = { onChapterClick(item) },
                    score = item.tracker.displayScore(item.track)
                        .takeIf { supportsScoring && item.track.score != 0.0 },
                    onScoreClick = { onScoreClick(item) }
                        .takeIf { supportsScoring },
                    startDate = remember(item.track.startDate) { dateFormat.format(item.track.startDate.toLocalDate()) }
                        .takeIf { supportsReadingDates && item.track.startDate != 0L },
                    onStartDateClick = { onStartDateEdit(item) } // TODO
                        .takeIf { supportsReadingDates },
                    endDate = dateFormat.format(item.track.finishDate.toLocalDate())
                        .takeIf { supportsReadingDates && item.track.finishDate != 0L },
                    onEndDateClick = { onEndDateEdit(item) }
                        .takeIf { supportsReadingDates },
                    onNewSearch = { onNewSearch(item) },
                    onOpenInBrowser = { onOpenInBrowser(item) },
                    onRemoved = { onRemoved(item) },
                )
            } else {
                TrackInfoItemEmpty(
                    tracker = item.tracker,
                    onNewSearch = { onNewSearch(item) },
                )
            }
        }
    }
}

@Composable
private fun TrackerAndUrlRow(
    trackItems: List<TrackItem>,
    webUrlProvider: () -> List<String>?,
    onNewIdSearch: (TrackerChipElement) -> Unit,
    onNewChipSearch: (TrackerChipElement) -> Unit,
    onOpenChipElementInBrowser: (TrackerChipElement) -> Unit,
) {
    val trackerChipElements = webUrlProvider()
        ?.map { TrackerChipElement(it, trackItems) }
        ?.filter { it.trackItem?.track?.remoteId != it.mangaId || it.trackItem?.track == null }
        ?.sortedBy { it.serviceId }
        ?.sortedWith(compareBy(nullsLast()) { it.serviceId })
    if (!trackerChipElements.isNullOrEmpty()) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(end = 12.dp)
                .animateContentSize(),
        ) {
            val context = LocalContext.current
            var showMenu by remember { mutableStateOf(false) }
            var showBrowserConfirmationDialog by remember { mutableStateOf(false) }
            var selectedChipElement: TrackerChipElement? by remember { mutableStateOf(null) }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (selectedChipElement?.trackItem?.tracker != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_open_in_mihon)) },
                        onClick = {
                            if (selectedChipElement!!.mangaId != null) {
                                onNewIdSearch(selectedChipElement!!)
                            } else if (selectedChipElement!!.searchQuery != null) {
                                onNewChipSearch(selectedChipElement!!)
                            }
                            showMenu = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_copy_to_clipboard)) },
                    onClick = {
                        context.copyToClipboard(selectedChipElement?.url!!, selectedChipElement?.url!!)
                        showMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_open_in_browser)) },
                    onClick = {
                        if (selectedChipElement?.potentiallyUnsafeUrl == true) {
                            showBrowserConfirmationDialog = true
                        } else {
                            onOpenChipElementInBrowser(selectedChipElement!!)
                        }
                        showMenu = false
                    },
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = MaterialTheme.padding.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(items = trackerChipElements) { chipElement ->
                    TrackerChip(
                        trackerChipElement = chipElement,
                        modifier = Modifier,
                        onClick = {
                            selectedChipElement = chipElement
                            showMenu = true
                        },
                    )
                }
            }
            if (showBrowserConfirmationDialog) {
                OpenBrowserConfirmationDialog(
                    onDismissRequest = { showBrowserConfirmationDialog = false },
                    onConfirmation = {
                        showBrowserConfirmationDialog = false
                        onOpenChipElementInBrowser(selectedChipElement!!)
                    },
                    url = selectedChipElement!!.url,
                    host = selectedChipElement!!.hostName,
                )
            }
        }
    }
}

@Composable
private fun TrackInfoItem(
    title: String,
    tracker: Tracker,
    status: StringResource?,
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
    val context = LocalContext.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackLogoIcon(
                tracker = tracker,
                onClick = onOpenInBrowser,
            )
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f)
                    .combinedClickable(
                        onClick = onNewSearch,
                        onLongClick = {
                            context.copyToClipboard(title, title)
                        },
                    )
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Column {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackDetailsItem(
                        modifier = Modifier.weight(1f),
                        text = status?.let { stringResource(it) } ?: "",
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
                            text = score ?: stringResource(MR.strings.score),
                            onClick = onScoreClick,
                        )
                    }
                }

                if (onStartDateClick != null && onEndDateClick != null) {
                    HorizontalDivider()
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        TrackDetailsItem(
                            modifier = Modifier.weight(1F),
                            text = startDate,
                            placeholder = stringResource(MR.strings.track_started_reading_date),
                            onClick = onStartDateClick,
                        )
                        VerticalDivider()
                        TrackDetailsItem(
                            modifier = Modifier.weight(1F),
                            text = endDate,
                            placeholder = stringResource(MR.strings.track_finished_reading_date),
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
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text ?: placeholder,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (text == null) UnsetStatusTextAlpha else 1f),
        )
    }
}

@Composable
private fun TrackInfoItemEmpty(
    tracker: Tracker,
    onNewSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackLogoIcon(tracker)
        TextButton(
            onClick = onNewSearch,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(text = stringResource(MR.strings.add_tracking))
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
                contentDescription = stringResource(MR.strings.label_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_open_in_browser)) },
                onClick = {
                    onOpenInBrowser()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_remove)) },
                onClick = {
                    onRemoved()
                    expanded = false
                },
            )
        }
    }
}

@Composable fun TrackerChip(
    trackerChipElement: TrackerChipElement,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        SuggestionChip(
            modifier = modifier,
            onClick = onClick,
            colors = SuggestionChipDefaults.suggestionChipColors().copy(),
            icon = {
                Icon(
                    imageVector = trackerChipElement.icon,
                    contentDescription = null,
                )
            },
            label = {
                Text(
                    text = trackerChipElement.trackerName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            },
        )
    }
}

@Composable
private fun OpenBrowserConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    url: String,
    host: String,
) {
    AlertDialog(
        title = { Text(text = stringResource(MR.strings.label_warning)) },
        text = {
            Text(
                text = stringResource(
                    MR.strings.potentially_unsafe_website_warning,
                    host,
                    url,
                ),
            )
        },
        onDismissRequest = { onDismissRequest() },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                },
            ) {
                Text(stringResource(MR.strings.action_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun TrackInfoDialogHomePreviews(
    @PreviewParameter(TrackInfoDialogHomePreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme {
        Surface {
            content()
        }
    }
}
