package eu.kanade.tachiyomi.data.library

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.chop
import eu.kanade.tachiyomi.util.notification
import eu.kanade.tachiyomi.util.notificationManager

class LibraryUpdateNotifier(private val context: Context) {
    /**
     * Bitmap of the app for notifications.
     */
    val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    /**
     * Shows the notification containing the result of the update done by the service.
     *
     * @param updates a list of manga with new updates.
     */
    fun showResultNotification(updates: List<Manga>) {
        val newUpdates = updates.map { it.title.chop(45) }.toMutableSet()

        // Append new chapters from a previous, existing notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val previousNotification = context.notificationManager.activeNotifications
                    .find { it.id == Notifications.ID_LIBRARY_RESULT }

            if (previousNotification != null) {
                val oldUpdates = previousNotification.notification.extras
                        .getString(Notification.EXTRA_BIG_TEXT)

                if (!oldUpdates.isNullOrEmpty()) {
                    newUpdates += oldUpdates.split("\n")
                }
            }
        }

        context.notificationManager.notify(Notifications.ID_LIBRARY_RESULT, context.notification(Notifications.CHANNEL_LIBRARY) {
            setSmallIcon(R.drawable.ic_book_white_24dp)
            setLargeIcon(notificationBitmap)
            setContentTitle(context.getString(R.string.notification_new_chapters))
            if (newUpdates.size > 1) {
                setContentText(context.getString(R.string.notification_new_chapters_text, newUpdates.size))
                setStyle(NotificationCompat.BigTextStyle().bigText(newUpdates.joinToString("\n")))
                setNumber(newUpdates.size)
            } else {
                setContentText(newUpdates.first())
            }
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(getNotificationIntent(context))
            setAutoCancel(true)
        })
    }

    /**
     * Returns an intent to open the main activity.
     */
    private fun getNotificationIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.action = MainActivity.SHORTCUT_RECENTLY_UPDATED
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}