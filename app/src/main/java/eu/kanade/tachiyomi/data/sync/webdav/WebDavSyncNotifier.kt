package eu.kanade.tachiyomi.data.sync.webdav

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@Inject
class WebDavSyncNotifier(
    private val context: Context,
) {
    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SYNC_PROGRESS,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_mihon)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SYNC_COMPLETE,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_mihon)
        setAutoCancel(true)
    }

    fun showProgress(): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.webdav_sync_in_progress))
            setProgress(0, 0, true)
        }
        context.notify(Notifications.ID_SYNC_PROGRESS, builder.build())
        return builder
    }

    fun showComplete(summary: WebDavSyncSummary) {
        context.cancelNotification(Notifications.ID_SYNC_PROGRESS)

        val missingCount = summary.missingExtensions.size
        val contentText = if (missingCount > 0) {
            context.stringResource(MR.strings.webdav_sync_complete_missing_extensions, missingCount)
        } else {
            context.stringResource(MR.strings.webdav_sync_complete)
        }

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.webdav_sync_title))
            setContentText(contentText)
            setContentIntent(openSyncSettingsIntent())
            context.notify(Notifications.ID_SYNC_COMPLETE, build())
        }
    }

    fun showError(error: String?) {
        context.cancelNotification(Notifications.ID_SYNC_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.webdav_sync_failed))
            setContentText(error)
            setContentIntent(openSyncSettingsIntent())
            context.notify(Notifications.ID_SYNC_COMPLETE, build())
        }
    }

    private fun openSyncSettingsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Constants.SHORTCUT_WEBDAV_SYNC
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
