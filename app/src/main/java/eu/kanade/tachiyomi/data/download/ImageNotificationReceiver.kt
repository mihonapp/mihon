package eu.kanade.tachiyomi.data.download

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.notificationManager
import java.io.File

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

    fun deleteImage(path: String) {
        val file = File(path)
        if (file.exists()) file.delete()
    }

    fun shareImage(context: Context, path: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            type = "image/jpeg"
        }
        context.startActivity(Intent.createChooser(shareIntent, context.resources.getText(R.string.action_share))
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK })
    }

    fun showImage(context: Context, path: String) {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            setDataAndType(Uri.parse("file://" + path), "image/*")
        }
        context.startActivity(intent)
    }

    companion object {
        const val ACTION_SHARE_IMAGE = "eu.kanade.SHARE_IMAGE"

        const val ACTION_SHOW_IMAGE = "eu.kanade.SHOW_IMAGE"

        const val ACTION_DELETE_IMAGE = "eu.kanade.DELETE_IMAGE"

        const val EXTRA_FILE_LOCATION = "file_location"

        const val NOTIFICATION_ID = "notification_id"

        fun shareImageIntent(context: Context, path: String, notificationId: Int): PendingIntent {
            val intent = Intent(context, ImageNotificationReceiver::class.java).apply {
                action = ACTION_SHARE_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
                putExtra(NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun showImageIntent(context: Context, path: String): PendingIntent {
            val intent = Intent(context, ImageNotificationReceiver::class.java).apply {
                action = ACTION_SHOW_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun deleteImageIntent(context: Context, path: String, notificationId: Int): PendingIntent {
            val intent = Intent(context, ImageNotificationReceiver::class.java).apply {
                action = ACTION_DELETE_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
                putExtra(NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
