package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit

class BackupNotifier(private val context: Context) {

    private val preferences: SecurityPreferences by injectLazy()

    private val progressNotificationBuilder = context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_tachi)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_tachi)
        setAutoCancel(false)
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun showBackupProgress(): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(context.getString(R.string.creating_backup))

            setProgress(0, 0, true)
        }

        builder.show(Notifications.ID_BACKUP_PROGRESS)

        return builder
    }

    fun showBackupError(error: String?) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.creating_backup_error))
            setContentText(error)

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showBackupComplete(unifile: UniFile) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.backup_created))
            setContentText(unifile.filePath ?: unifile.name)

            clearActions()
            addAction(
                R.drawable.ic_share_24dp,
                context.getString(R.string.action_share),
                NotificationReceiver.shareBackupPendingBroadcast(context, unifile.uri, Notifications.ID_BACKUP_COMPLETE),
            )

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showRestoreProgress(content: String = "", contentTitle: String = context.getString(R.string.restoring_backup), progress: Int = 0, maxAmount: Int = 100): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(contentTitle)

            if (!preferences.hideNotificationContent().get()) {
                setContentText(content)
            }

            setProgress(maxAmount, progress, false)
            setOnlyAlertOnce(true)

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.getString(R.string.action_cancel),
                NotificationReceiver.cancelRestorePendingBroadcast(context, Notifications.ID_RESTORE_PROGRESS),
            )
        }

        builder.show(Notifications.ID_RESTORE_PROGRESS)

        return builder
    }

    fun showRestoreError(error: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.getString(R.string.restoring_backup_error))
            setContentText(error)

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }

    fun showRestoreComplete(time: Long, errorCount: Int, path: String?, file: String?, contentTitle: String = context.getString(R.string.restore_completed)) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        val timeString = context.getString(
            R.string.restore_duration,
            TimeUnit.MILLISECONDS.toMinutes(time),
            TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(
                TimeUnit.MILLISECONDS.toMinutes(time),
            ),
        )

        with(completeNotificationBuilder) {
            setContentTitle(contentTitle)
            setContentText(context.resources.getQuantityString(R.plurals.restore_completed_message, errorCount, timeString, errorCount))

            clearActions()
            if (errorCount > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
                val destFile = File(path, file)
                val uri = destFile.getUriCompat(context)

                val errorLogIntent = NotificationReceiver.openErrorLogPendingActivity(context, uri)
                setContentIntent(errorLogIntent)
                addAction(
                    R.drawable.ic_folder_24dp,
                    context.getString(R.string.action_show_errors),
                    errorLogIntent,
                )
            }

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }
}
