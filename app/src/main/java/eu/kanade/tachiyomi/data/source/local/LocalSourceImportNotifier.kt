package eu.kanade.tachiyomi.data.source.local

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class LocalSourceImportNotifier(private val context: Context) {

    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_LOCAL_SOURCE_IMPORT,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_mihon)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_LOCAL_SOURCE_IMPORT,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_mihon)
        setAutoCancel(true)
    }

    fun showImportProgress(
        progress: Int,
        maxAmount: Int,
        folderName: String,
    ): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(folderName)
            setContentText(context.stringResource(MR.strings.importing_local_source_progress, progress, maxAmount))
            setProgress(maxAmount, progress, false)
        }

        context.notify(Notifications.ID_LOCAL_SOURCE_IMPORT_PROGRESS, builder.build())

        return builder
    }

    fun showImportError(error: String?) {
        context.cancelNotification(Notifications.ID_LOCAL_SOURCE_IMPORT_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.import_local_source_error))
            setContentText(error)

            context.notify(Notifications.ID_LOCAL_SOURCE_IMPORT_COMPLETE, build())
        }
    }

    fun showImportComplete(folderName: String) {
        context.cancelNotification(Notifications.ID_LOCAL_SOURCE_IMPORT_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.import_local_source_success, folderName))
            setContentText(null)

            context.notify(Notifications.ID_LOCAL_SOURCE_IMPORT_COMPLETE, build())
        }
    }
}
