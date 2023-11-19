package eu.kanade.tachiyomi.data.updater

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.i18n.stringResource
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR

internal class AppUpdateNotifier(private val context: Context) {

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_APP_UPDATE)

    /**
     * Call to show notification.
     *
     * @param id id of the notification channel.
     */
    private fun NotificationCompat.Builder.show(id: Int = Notifications.ID_APP_UPDATER) {
        context.notify(id, build())
    }

    fun cancel() {
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATER)
    }

    @SuppressLint("LaunchActivityFromNotification")
    fun promptUpdate(release: Release) {
        val updateIntent = NotificationReceiver.downloadAppUpdatePendingBroadcast(
            context,
            release.getDownloadLink(),
            release.version,
        )

        val releaseIntent = Intent(Intent.ACTION_VIEW, release.releaseLink.toUri()).run {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                context,
                release.hashCode(),
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.update_check_notification_update_available))
            setContentText(release.version)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentIntent(updateIntent)

            clearActions()
            addAction(
                android.R.drawable.stat_sys_download_done,
                context.stringResource(MR.strings.action_download),
                updateIntent,
            )
            addAction(
                R.drawable.ic_info_24dp,
                context.stringResource(MR.strings.whats_new),
                releaseIntent,
            )
        }
        notificationBuilder.show()
    }

    /**
     * Call when apk download starts.
     *
     * @param title tile of notification.
     */
    fun onDownloadStarted(title: String? = null): NotificationCompat.Builder {
        with(notificationBuilder) {
            title?.let { setContentTitle(title) }
            setContentText(context.stringResource(MR.strings.update_check_notification_download_in_progress))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.cancelDownloadAppUpdatePendingBroadcast(context),
            )
        }
        notificationBuilder.show()
        return notificationBuilder
    }

    /**
     * Call when apk download progress changes.
     *
     * @param progress progress of download (xx%/100).
     */
    fun onProgressChange(progress: Int) {
        with(notificationBuilder) {
            setProgress(100, progress, false)
            setOnlyAlertOnce(true)
        }
        notificationBuilder.show()
    }

    /**
     * Call when apk download is finished.
     *
     * @param uri path location of apk.
     */
    fun promptInstall(uri: Uri) {
        val installIntent = NotificationHandler.installApkPendingActivity(context, uri)
        with(notificationBuilder) {
            setContentText(context.stringResource(MR.strings.update_check_notification_download_complete))
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setOnlyAlertOnce(false)
            setProgress(0, 0, false)
            setContentIntent(installIntent)
            setOngoing(true)

            clearActions()
            addAction(
                R.drawable.ic_system_update_alt_white_24dp,
                context.stringResource(MR.strings.action_install),
                installIntent,
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_PROMPT),
            )
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_PROMPT)
    }

    /**
     * Some people are still installing the app from F-Droid, so we avoid prompting GitHub-based
     * updates.
     *
     * We can prompt them to migrate to the GitHub version though.
     */
    fun promptFdroidUpdate() {
        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.update_check_notification_update_available))
            setContentText(context.stringResource(MR.strings.update_check_fdroid_migration_info))
            setSmallIcon(R.drawable.ic_tachi)
            setContentIntent(
                NotificationHandler.openUrl(
                    context,
                    "https://tachiyomi.org/docs/faq/general#how-do-i-update-from-the-f-droid-builds",
                ),
            )
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATE_PROMPT)
    }

    /**
     * Call when apk download throws a error
     *
     * @param url web location of apk to download.
     */
    fun onDownloadError(url: String) {
        with(notificationBuilder) {
            setContentText(context.stringResource(MR.strings.update_check_notification_download_error))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOnlyAlertOnce(false)
            setProgress(0, 0, false)

            clearActions()
            addAction(
                R.drawable.ic_refresh_24dp,
                context.stringResource(MR.strings.action_retry),
                NotificationReceiver.downloadAppUpdatePendingBroadcast(context, url),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATER),
            )
        }
        notificationBuilder.show(Notifications.ID_APP_UPDATER)
    }
}
