package eu.kanade.tachiyomi.util

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.support.annotation.StringRes
import android.support.v4.app.NotificationCompat
import android.widget.Toast

/**
 * Display a toast in this context.
 * @param resource the text resource.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(@StringRes resource: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resource, duration).show()
}

/**
 * Helper method to create a notification.
 * @param func the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
inline fun Context.notification(func: NotificationCompat.Builder.() -> Unit): Notification {
    val builder = NotificationCompat.Builder(this)
    builder.func()
    return builder.build()
}

/**
 * Property to get the notification manager from the context.
 */
val Context.notificationManager : NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

/**
 * Property to get the alarm manager from the context.
 * @return the alarm manager.
 */
val Context.alarmManager: AlarmManager
    get() = getSystemService(Context.ALARM_SERVICE) as AlarmManager