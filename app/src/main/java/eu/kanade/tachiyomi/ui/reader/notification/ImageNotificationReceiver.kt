package eu.kanade.tachiyomi.ui.reader.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.content.FileProvider
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.notificationManager
import java.io.File

/**
 * The BroadcastReceiver of [ImageNotifier]
 * Intent calls should be made from this class.
 */
class ImageNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHARE_IMAGE -> {
                shareImage(context, intent.getStringExtra(EXTRA_FILE_LOCATION))
                context.notificationManager.cancel(intent.getIntExtra(NOTIFICATION_ID, 5))
            }
            ACTION_SHOW_IMAGE ->
                showImage(context, intent.getStringExtra(EXTRA_FILE_LOCATION))
            ACTION_DELETE_IMAGE -> {
                deleteImage(intent.getStringExtra(EXTRA_FILE_LOCATION))
                context.notificationManager.cancel(intent.getIntExtra(NOTIFICATION_ID, 5))
            }
        }
    }

    /**
     * Called to delete image
     * @param path path of file
     */
    private fun deleteImage(path: String) {
        val file = File(path)
        if (file.exists()) file.delete()
    }

    /**
     * Called to start share intent to share image
     * @param context context of application
     * @param path path of file
     */
    private fun shareImage(context: Context, path: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
            type = "image/jpeg"
        }
        context.startActivity(Intent.createChooser(shareIntent, context.resources.getText(R.string.action_share))
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK })
    }

    /**
     * Called to show image in gallery application
     * @param context context of application
     * @param path path of file
     */
    private fun showImage(context: Context, path: String) {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            val uri = FileProvider.getUriForFile(context,"eu.kanade.tachiyomi.provider",File(path))
            setDataAndType(uri, "image/*")
        }
        context.startActivity(intent)
    }

    companion object {
        private const val ACTION_SHARE_IMAGE = "eu.kanade.SHARE_IMAGE"

        private const val ACTION_SHOW_IMAGE = "eu.kanade.SHOW_IMAGE"

        private const val ACTION_DELETE_IMAGE = "eu.kanade.DELETE_IMAGE"

        private const val EXTRA_FILE_LOCATION = "file_location"

        private const val NOTIFICATION_ID = "notification_id"

        internal fun shareImageIntent(context: Context, path: String, notificationId: Int): PendingIntent {
            val intent = Intent(context, ImageNotificationReceiver::class.java).apply {
                action = ACTION_SHARE_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
                putExtra(NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        internal fun showImageIntent(context: Context, path: String): PendingIntent {
            val intent = Intent(context, ImageNotificationReceiver::class.java).apply {
                action = ACTION_SHOW_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        internal fun deleteImageIntent(context: Context, path: String, notificationId: Int): PendingIntent {
            val intent = Intent(context, ImageNotificationReceiver::class.java).apply {
                action = ACTION_DELETE_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
                putExtra(NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
