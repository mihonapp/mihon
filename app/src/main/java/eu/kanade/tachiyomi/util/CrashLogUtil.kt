package eu.kanade.tachiyomi.util

import android.content.Context
import android.net.Uri
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.lang.withUIContext

class CrashLogUtil(private val context: Context) {

    suspend fun dumpLogs() = withNonCancellableContext {
        try {
            val file = context.createFileInCacheDir("tachiyomi_crash_logs.txt")
            Runtime.getRuntime().exec("logcat *:E -d -f ${file.absolutePath}").waitFor()
            file.appendText(getDebugInfo())

            showNotification(file.getUriCompat(context))
        } catch (e: Throwable) {
            withUIContext { context.toast("Failed to get logs") }
        }
    }

    fun getDebugInfo(): String {
        return """
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Android build ID: ${Build.DISPLAY}
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE}
            Device model: ${Build.MODEL}
            Device product name: ${Build.PRODUCT}
        """.trimIndent()
    }

    private fun showNotification(uri: Uri) {
        context.notificationManager.cancel(Notifications.ID_CRASH_LOGS)

        context.notify(
            Notifications.ID_CRASH_LOGS,
            Notifications.CHANNEL_CRASH_LOGS,
        ) {
            setContentTitle(context.getString(R.string.crash_log_saved))
            setSmallIcon(R.drawable.ic_tachi)

            clearActions()
            addAction(
                R.drawable.ic_folder_24dp,
                context.getString(R.string.action_open_log),
                NotificationReceiver.openErrorLogPendingActivity(context, uri),
            )
            addAction(
                R.drawable.ic_share_24dp,
                context.getString(R.string.action_share),
                NotificationReceiver.shareCrashLogPendingBroadcast(context, uri, Notifications.ID_CRASH_LOGS),
            )
        }
    }
}
