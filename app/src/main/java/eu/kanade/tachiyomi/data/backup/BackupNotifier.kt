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
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BackupNotifier(private val context: Context) {

    private val lock = ReentrantLock()

    private val preferences: SecurityPreferences by injectLazy()

    private val largeIcon by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    private val completeNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE) {
            setLargeIcon(largeIcon)
            setSmallIcon(R.drawable.ic_mihon)
            setAutoCancel(false)
        }
    }

    private fun newProgressBuilder(): NotificationCompat.Builder {
        return context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS) {
            setLargeIcon(largeIcon)
            setSmallIcon(R.drawable.ic_mihon)
            setAutoCancel(false)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
    }

    private var progressNotificationBuilder: NotificationCompat.Builder? = null

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun showBackupProgress(): NotificationCompat.Builder {
        val builder = newProgressBuilder()
            .setContentTitle(context.stringResource(MR.strings.creating_backup))
            .setProgress(0, 0, true)

        builder.show(Notifications.ID_BACKUP_PROGRESS)

        return builder
    }

    fun showBackupError(error: String?) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.creating_backup_error))
            setContentText(error)

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showBackupComplete(file: UniFile) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.backup_created))
            setContentText(file.displayablePath)

            clearActions()
            addAction(
                R.drawable.ic_share_24dp,
                context.stringResource(MR.strings.action_share),
                NotificationReceiver.shareBackupPendingActivity(context, file.uri),
            )

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showRestoreProgress(
        content: String = "",
        progress: Int = 0,
        maxAmount: Int = 100,
        isSync: Boolean = false,
    ): NotificationCompat.Builder {
        lock.withLock {
            val builder = (
                progressNotificationBuilder ?: newProgressBuilder().also {
                    progressNotificationBuilder = it
                }
                )
            with(builder) {
                setContentTitle(
                    if (isSync) {
                        context.stringResource(MR.strings.syncing_library)
                    } else {
                        context.stringResource(MR.strings.restoring_backup)
                    },
                )
                setProgress(maxAmount, progress, false)
                setOnlyAlertOnce(true)
                clearActions()
                addAction(
                    R.drawable.ic_close_24dp,
                    context.stringResource(MR.strings.action_cancel),
                    NotificationReceiver.cancelRestorePendingBroadcast(context, Notifications.ID_RESTORE_PROGRESS),
                )
                if (!preferences.hideNotificationContent().get() && content.isNotEmpty()) {
                    setContentText(content)
                } else if (preferences.hideNotificationContent().get()) {
                    setContentText(null)
                }
                show(Notifications.ID_RESTORE_PROGRESS)
            }
            return builder
        }
    }

    fun showRestoreError(error: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.restoring_backup_error))
            setContentText(error)

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }

    fun showRestoreComplete(
        time: Long,
        errorCount: Int,
        path: String?,
        file: String?,
        isSync: Boolean,
    ) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        val contentTitle = if (isSync) {
            context.stringResource(MR.strings.library_sync_complete)
        } else {
            context.stringResource(MR.strings.restore_completed)
        }

        val timeString = context.stringResource(
            MR.strings.restore_duration,
            TimeUnit.MILLISECONDS.toMinutes(time),
            TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(
                TimeUnit.MILLISECONDS.toMinutes(time),
            ),
        )

        with(completeNotificationBuilder) {
            setContentTitle(contentTitle)
            setContentText(
                context.pluralStringResource(
                    MR.plurals.restore_completed_message,
                    errorCount,
                    timeString,
                    errorCount,
                ),
            )

            clearActions()
            if (errorCount > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
                val destFile = File(path, file)
                val uri = destFile.getUriCompat(context)

                val errorLogIntent = NotificationReceiver.openErrorLogPendingActivity(context, uri)
                setContentIntent(errorLogIntent)
                addAction(
                    R.drawable.ic_folder_24dp,
                    context.stringResource(MR.strings.action_show_errors),
                    errorLogIntent,
                )
            }

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }
}
