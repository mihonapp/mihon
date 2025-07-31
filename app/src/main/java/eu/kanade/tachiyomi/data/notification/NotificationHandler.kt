package eu.kanade.tachiyomi.data.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.core.common.Constants

/**
 * Class that manages [PendingIntent] of activity's
 */
object NotificationHandler {
    /**
     * Returns [PendingIntent] that starts a download activity.
     *
     * @param context context of application
     */
    internal fun openDownloadManagerPendingActivity(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Constants.SHORTCUT_DOWNLOADS
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns [PendingIntent] that opens failed updates screen.
     *
     * @param context context of application
     */
    internal fun openFailedUpdatesPendingActivity(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Constants.SHORTCUT_FAILED
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns [PendingIntent] that starts a gallery activity
     *
     * @param context context of application
     */
    internal fun openImagePendingActivity(context: Context, uri: Uri): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns [PendingIntent] that prompts user with apk install intent
     *
     * @param context context
     * @param uri uri of apk that is installed
     */
    fun installApkPendingActivity(context: Context, uri: Uri): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ExtensionInstaller.APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    fun openUrl(context: Context, url: String): PendingIntent {
        val notificationIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        return PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
    }
}
