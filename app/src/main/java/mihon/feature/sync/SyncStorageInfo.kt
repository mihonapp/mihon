package mihon.feature.sync

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.sync.SyncUsage
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * What the synchronisation occupies, on the device and on the account.
 *
 * Measured when the screen opens rather than kept current: the remote half costs network round
 * trips, and this is a figure people look up occasionally, not one they watch.
 */
@Composable
fun SyncStorageInfo(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val usage by produceState<SyncUsage.Usage?>(initialValue = null) {
        value = runCatching { context.appGraph.syncUsage.read() }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not measure the sync storage" } }
            .getOrElse { SyncUsage.Usage() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        val measured = usage
        if (measured == null) {
            Text(
                text = stringResource(MR.strings.loading),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        Text(
            text = stringResource(
                MR.strings.pref_sync_storage_remote,
                Formatter.formatFileSize(context, measured.remoteBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                MR.strings.pref_sync_storage_local,
                Formatter.formatFileSize(context, measured.localBackupBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        if (!measured.hasQuota) {
            Text(
                text = stringResource(MR.strings.pref_sync_storage_quota_unknown),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        val used = measured.quotaUsedBytes ?: 0L
        val limit = measured.quotaLimitBytes ?: 0L

        LinearProgressIndicator(
            progress = { (used.toFloat() / limit).coerceIn(0f, 1f) },
            modifier = Modifier
                .padding(top = MaterialTheme.padding.small)
                .clip(MaterialTheme.shapes.small)
                .fillMaxWidth()
                .height(12.dp),
        )
        Text(
            text = stringResource(
                MR.strings.pref_sync_storage_quota,
                Formatter.formatFileSize(context, used),
                Formatter.formatFileSize(context, limit),
            ),
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
