package eu.kanade.tachiyomi.data.library

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.alarmManager

/**
 * This class is used to update the library by firing an alarm after a specified time.
 * It has a receiver reacting to system's boot and the intent fired by this alarm.
 * See [onReceive] for more information.
 */
class LibraryUpdateAlarm : BroadcastReceiver() {

    companion object {
        const val LIBRARY_UPDATE_ACTION = "eu.kanade.UPDATE_LIBRARY"

        /**
         * Sets the alarm to run the intent that updates the library.
         * @param context the application context.
         * @param intervalInHours the time in hours when it will be executed. Defaults to the
         * value stored in preferences.
         */
        @JvmStatic
        @JvmOverloads
        fun startAlarm(context: Context,
                       intervalInHours: Int = PreferencesHelper.getLibraryUpdateInterval(context)) {
            // Stop previous running alarms if needed, and do not restart it if the interval is 0.
            stopAlarm(context)
            if (intervalInHours == 0)
                return

            // Get the time the alarm should fire the event to update.
            val intervalInMillis = intervalInHours * 60 * 60 * 1000
            val nextRun = SystemClock.elapsedRealtime() + intervalInMillis

            // Start the alarm.
            val pendingIntent = getPendingIntent(context)
            context.alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextRun, intervalInMillis.toLong(), pendingIntent)
        }

        /**
         * Stops the alarm if it's running.
         * @param context the application context.
         */
        fun stopAlarm(context: Context) {
            val pendingIntent = getPendingIntent(context)
            context.alarmManager.cancel(pendingIntent)
        }

        /**
         * Get the intent the alarm should run when it's fired.
         * @param context the application context.
         * @return the intent that will run when the alarm is fired.
         */
        private fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, LibraryUpdateAlarm::class.java)
            intent.action = LIBRARY_UPDATE_ACTION
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    /**
     * Handle the intents received by this [BroadcastReceiver].
     * @param context the application context.
     * @param intent the intent to process.
     */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Start the alarm when the system is booted.
            Intent.ACTION_BOOT_COMPLETED -> startAlarm(context)
            // Update the library when the alarm fires an event.
            LIBRARY_UPDATE_ACTION -> LibraryUpdateService.start(context)
        }
    }

}
