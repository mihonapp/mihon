package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import androidx.core.content.getSystemService
import eu.kanade.tachiyomi.R

val Context.notificationManager: NotificationManager
    get() = getSystemService()!!

fun Context.notify(id: Int, channelId: String, block: (NotificationCompat.Builder.() -> Unit)? = null) {
    val notification = notificationBuilder(channelId, block).build()
    this.notify(id, notification)
}

@SuppressLint("MissingPermission")
fun Context.notify(id: Int, notification: Notification) {
    // TODO: check for permission once targeting SDK 33+
//    if (PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
//        return
//    }

    NotificationManagerCompat.from(this).notify(id, notification)
}

@SuppressLint("MissingPermission")
fun Context.notify(notificationWithIdAndTags: List<NotificationWithIdAndTag>) {
    // TODO: check for permission once targeting SDK 33+
//    if (PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
//        return
//    }

    NotificationManagerCompat.from(this).notify(notificationWithIdAndTags)
}

fun Context.cancelNotification(id: Int) {
    NotificationManagerCompat.from(this).cancel(id)
}

/**
 * Helper method to create a notification builder.
 *
 * @param id the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
fun Context.notificationBuilder(channelId: String, block: (NotificationCompat.Builder.() -> Unit)? = null): NotificationCompat.Builder {
    val builder = NotificationCompat.Builder(this, channelId)
        .setColor(getColor(R.color.accent_blue))
    if (block != null) {
        builder.block()
    }
    return builder
}

/**
 * Helper method to build a notification channel group.
 *
 * @param channelId the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification channel group to be displayed or updated.
 */
fun buildNotificationChannelGroup(
    channelId: String,
    block: (NotificationChannelGroupCompat.Builder.() -> Unit),
): NotificationChannelGroupCompat {
    val builder = NotificationChannelGroupCompat.Builder(channelId)
    builder.block()
    return builder.build()
}

/**
 * Helper method to build a notification channel.
 *
 * @param channelId the channel id.
 * @param channelImportance the channel importance.
 * @param block the function that will execute inside the builder.
 * @return a notification channel to be displayed or updated.
 */
fun buildNotificationChannel(
    channelId: String,
    channelImportance: Int,
    block: (NotificationChannelCompat.Builder.() -> Unit),
): NotificationChannelCompat {
    val builder = NotificationChannelCompat.Builder(channelId, channelImportance)
    builder.block()
    return builder.build()
}
