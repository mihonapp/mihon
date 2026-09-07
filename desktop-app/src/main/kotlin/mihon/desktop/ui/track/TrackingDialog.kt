package mihon.desktop.ui.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.track.DesktopTrackRecord
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.TrackSearchResult
import mihon.desktop.track.TrackStatus

@Composable
fun TrackingDialog(
    mangaTitle: String,
    trackers: List<DesktopTracker>,
    currentTracks: List<DesktopTrackRecord>,
    onDismiss: () -> Unit,
    onSaveTrack: (DesktopTrackRecord) -> Unit,
    onUnbindTrack: (Long) -> Unit, // trackerId
    onSearchTrack: suspend (DesktopTracker, String) -> List<TrackSearchResult>,
) {
    val strings = LocalStrings.current
    var editingTrack by remember { mutableStateOf<DesktopTrackRecord?>(null) }
    var searchingTracker by remember { mutableStateOf<DesktopTracker?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.trackingTitle(mangaTitle)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("tracking-dialog"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trackers, key = { it.id }) { tracker ->
                        val boundTrack = currentTracks.find { it.trackerId == tracker.id }
                        TrackerRow(
                            tracker = tracker,
                            track = boundTrack,
                            onBind = { searchingTracker = tracker },
                            onEdit = { editingTrack = boundTrack },
                            onUnbind = { onUnbindTrack(tracker.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("tracking-dialog-close")) {
                Text(strings.dialogClose)
            }
        },
    )

    // Search dialog to bind new tracking entry
    searchingTracker?.let { tracker ->
        SearchTrackDialog(
            tracker = tracker,
            initialQuery = mangaTitle,
            onDismiss = { searchingTracker = null },
            onSelect = { result ->
                val newTrack = DesktopTrackRecord(
                    mangaId = currentTracks.firstOrNull()?.mangaId ?: 0L,
                    trackerId = tracker.id,
                    remoteId = result.remoteId,
                    title = result.title,
                    totalChapters = result.totalChapters,
                    trackingUrl = result.trackingUrl,
                )
                onSaveTrack(newTrack)
                searchingTracker = null
            },
            onSearch = { query -> onSearchTrack(tracker, query) },
        )
    }

    // Edit track details dialog
    editingTrack?.let { track ->
        EditTrackDetailsDialog(
            track = track,
            onDismiss = { editingTrack = null },
            onSave = { updated ->
                onSaveTrack(updated)
                editingTrack = null
            },
        )
    }
}

@Composable
private fun TrackerRow(
    tracker: DesktopTracker,
    track: DesktopTrackRecord?,
    onBind: () -> Unit,
    onEdit: () -> Unit,
    onUnbind: () -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .testTag("tracker-row-${tracker.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tracker.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (track != null) {
                    val statusStr = strings.trackStatusLabel(TrackStatus.fromValue(track.status))
                    Text(
                        "${track.title} • Ch. ${track.lastChapterRead.toInt()} / ${if (track.totalChapters > 0) track.totalChapters else "?"} • $statusStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        if (tracker.isLoggedIn) strings.trackingNotTracking else strings.trackingNotLoggedIn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (track == null) {
                    FilledTonalButton(
                        onClick = onBind,
                        modifier = Modifier.testTag("tracker-bind-${tracker.id}"),
                    ) {
                        Text(strings.trackingTrack)
                    }
                } else {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("tracker-edit-${tracker.id}"),
                    ) {
                        Text(strings.trackingEdit)
                    }
                    TextButton(
                        onClick = onUnbind,
                        modifier = Modifier.testTag("tracker-unbind-${tracker.id}"),
                    ) {
                        Text(strings.trackingRemove, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrackDialog(
    tracker: DesktopTracker,
    initialQuery: String,
    onDismiss: () -> Unit,
    onSelect: (TrackSearchResult) -> Unit,
    onSearch: suspend (String) -> List<TrackSearchResult>,
) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<TrackSearchResult>>(emptyList()) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.trackingSearchTitle(tracker.name)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).testTag("track-search-input"),
                        singleLine = true,
                    )
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                results = onSearch(query)
                            }
                        },
                        modifier = Modifier.testTag("track-search-button"),
                    ) {
                        Text(strings.trackingSearch)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(results) { res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(res) }
                                .padding(8.dp)
                                .testTag("track-result-${res.remoteId}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(res.title, fontWeight = FontWeight.Medium)
                                if (res.totalChapters > 0) {
                                    Text(strings.trackingTotalChapters(res.totalChapters), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.dialogCancel) }
        },
    )
}

@Composable
private fun EditTrackDetailsDialog(
    track: DesktopTrackRecord,
    onDismiss: () -> Unit,
    onSave: (DesktopTrackRecord) -> Unit,
) {
    val strings = LocalStrings.current
    var lastChapterRead by remember { mutableStateOf(track.lastChapterRead.toString()) }
    var score by remember { mutableStateOf(track.score.toString()) }
    var status by remember { mutableStateOf(track.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.trackingEditTitle(track.title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lastChapterRead,
                    onValueChange = { lastChapterRead = it },
                    label = { Text(strings.trackingChaptersRead) },
                    modifier = Modifier.fillMaxWidth().testTag("track-chapter-read-input"),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = score,
                    onValueChange = { score = it },
                    label = { Text(strings.trackingScore) },
                    modifier = Modifier.fillMaxWidth().testTag("track-score-input"),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrackStatus.entries.take(3).forEach { s ->
                        OutlinedButton(
                            onClick = { status = s.value },
                            modifier = Modifier.weight(1f).testTag("track-status-${s.value}"),
                        ) {
                            Text(
                                strings.trackStatusLabel(s),
                                fontWeight = if (status == s.value) FontWeight.Bold else FontWeight.Normal,
                                color = if (status ==
                                    s.value
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chap = lastChapterRead.toDoubleOrNull() ?: track.lastChapterRead
                    val sc = score.toDoubleOrNull() ?: track.score
                    onSave(track.copy(lastChapterRead = chap, score = sc, status = status))
                },
                modifier = Modifier.testTag("track-save-details-button"),
            ) {
                Text(strings.categorySave)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.dialogCancel) }
        },
    )
}
