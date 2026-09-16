package mihon.desktop.ui.tasks

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

const val DOWNLOADS_SCREEN_TEST_TAG = "downloads_screen"
const val DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG = "downloads_pause_all"
const val DOWNLOADS_RESUME_ALL_BUTTON_TEST_TAG = "downloads_resume_all"
const val DOWNLOADS_CLEAR_COMPLETED_BUTTON_TEST_TAG = "downloads_clear_completed"
const val DOWNLOAD_ITEM_TEST_TAG_PREFIX = "download_item_"
const val DOWNLOAD_READ_BUTTON_TEST_TAG_PREFIX = "download_read_"

@Composable
fun DownloadsScreen(
    queue: List<DesktopDownload>,
    isRunning: Boolean,
    speedBytesPerSec: Double,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onCancel: (chapterId: Long) -> Unit,
    onRetry: (chapterId: Long) -> Unit,
    onReadChapter: (mangaId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier,
    recoveryMessage: String? = null,
    storageError: String? = null,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(DOWNLOADS_SCREEN_TEST_TAG)
            .padding(16.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = strings.downloadsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                val speedText = formatSpeed(speedBytesPerSec)
                val activeCount = queue.count {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.QUEUED
                }
                Text(
                    text = if (isRunning && speedBytesPerSec > 0) {
                        strings.downloadsActiveSpeed(activeCount, speedText)
                    } else {
                        mihon.desktop.i18n.recoveryText(
                            "$activeCount active items",
                            "$activeCount 个下载任务",
                            "$activeCount 個下載工作",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    OutlinedButton(
                        onClick = onPauseAll,
                        modifier = Modifier.testTag(DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.downloadsPauseAll)
                    }
                } else {
                    Button(
                        onClick = onResumeAll,
                        modifier = Modifier.testTag(DOWNLOADS_RESUME_ALL_BUTTON_TEST_TAG),
                        enabled = queue.any {
                            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.downloadsResumeAll)
                    }
                }

                TextButton(
                    onClick = onClearCompleted,
                    modifier = Modifier.testTag(DOWNLOADS_CLEAR_COMPLETED_BUTTON_TEST_TAG),
                    enabled = queue.any { it.status == DownloadStatus.COMPLETED },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.downloadsClearCompleted)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        listOfNotNull(storageError, recoveryMessage).distinct().forEach { message ->
            Surface(
                color = if (message == storageError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("download-recovery-notice"),
            ) {
                Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (queue.isEmpty()) {
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
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = strings.downloadsEmptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(queue, key = { it.chapterId }) { item ->
                    DownloadCard(
                        download = item,
                        onCancel = { onCancel(item.chapterId) },
                        onRetry = { onRetry(item.chapterId) },
                        onRead = { onReadChapter(item.mangaId, item.chapterId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    download: DesktopDownload,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRead: () -> Unit,
) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DOWNLOAD_ITEM_TEST_TAG_PREFIX + download.chapterId),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.mangaTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = download.chapterName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                StatusBadge(download.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusDetail = when (download.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED -> {
                        if (download.totalPages > 0) {
                            strings.text(
                                UiText.DownloadProgress,
                                download.downloadedImages,
                                download.totalPages,
                                (
                                    download.progress *
                                        100
                                    ).toInt(),
                            )
                        } else {
                            strings.text(UiText.PreparingDownload)
                        }
                    }
                    DownloadStatus.COMPLETED -> strings.text(UiText.DownloadedPages, download.downloadedImages)
                    DownloadStatus.ERROR -> download.error ?: strings.text(UiText.DownloadFailed)
                }

                Text(
                    text = statusDetail,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (download.status ==
                        DownloadStatus.ERROR
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (download.status == DownloadStatus.COMPLETED) {
                        FilledTonalButton(
                            onClick = onRead,
                            modifier = Modifier.testTag(DOWNLOAD_READ_BUTTON_TEST_TAG_PREFIX + download.chapterId),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.downloadsRead)
                        }
                    }
                    if (download.status == DownloadStatus.ERROR) {
                        TextButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.downloadsRetry)
                        }
                    }
                    if (download.status != DownloadStatus.COMPLETED) {
                        TextButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.downloadsCancel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    val (color, label) = when (status) {
        DownloadStatus.QUEUED -> Color.Gray to "Queued"
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary to strings.downloadsStatusDownloading
        DownloadStatus.PAUSED -> Color(0xFFE6A23C) to strings.downloadsStatusPaused
        DownloadStatus.COMPLETED -> Color(0xFF67C23A) to strings.downloadsStatusCompleted
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error to strings.downloadsStatusError
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatSpeed(bytesPerSec: Double): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024)
        else -> String.format("%.0f B/s", bytesPerSec)
    }
}
