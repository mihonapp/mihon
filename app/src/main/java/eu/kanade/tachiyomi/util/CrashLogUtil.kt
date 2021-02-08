package eu.kanade.tachiyomi.util

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toast
import java.io.IOException

class CrashLogUtil(private val context: Context) {

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_CRASH_LOGS) {
        setSmallIcon(R.drawable.ic_tachi)
    }

    fun dumpLogs() {
        try {
            val file = context.createFileInCacheDir("tachiyomi_crash_logs.txt")
            Runtime.getRuntime().exec("logcat *:E -d -f ${file.absolutePath}")

            showNotification(file.getUriCompat(context))
        } catch (e: IOException) {
            context.toast("Failed to get logs")
        }
    }

    private fun showNotification(uri: Uri) {
        context.notificationManager.cancel(Notifications.ID_CRASH_LOGS)

        with(notificationBuilder) {
            setContentTitle(context.getString(R.string.crash_log_saved))

            // Clear old actions if they exist
            clearActions()

            addAction(
                R.drawable.ic_folder_24dp,
                context.getString(R.string.action_open_log),
                NotificationReceiver.openErrorLogPendingActivity(context, uri)
            )

            addAction(
                R.drawable.ic_share_24dp,
                context.getString(R.string.action_share),
                NotificationReceiver.shareCrashLogPendingBroadcast(context, uri, Notifications.ID_CRASH_LOGS)
            )

            context.notificationManager.notify(Notifications.ID_CRASH_LOGS, build())
        }
    }
}
