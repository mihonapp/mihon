package eu.kanade.tachiyomi.data.sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import timber.log.Timber;

public class LibraryUpdateAlarm extends BroadcastReceiver {

    public static final String LIBRARY_UPDATE_ACTION = "eu.kanade.UPDATE_LIBRARY";

    public static void startAlarm(Context context) {
        startAlarm(context, PreferencesHelper.getLibraryUpdateInterval(context));
    }

    public static void startAlarm(Context context, int intervalInHours) {
        stopAlarm(context);
        if (intervalInHours == 0)
            return;

        int intervalInMillis = intervalInHours * 60 * 60 * 1000;
        long nextRun = SystemClock.elapsedRealtime() + intervalInMillis;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextRun, intervalInMillis, pendingIntent);

        Timber.i("Alarm set. Library will update on " + nextRun);
    }

    public static void stopAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        alarmManager.cancel(pendingIntent);
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, LibraryUpdateAlarm.class);
        intent.setAction(LIBRARY_UPDATE_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null)
            return;

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            startAlarm(context);
        } else if (intent.getAction().equals(LIBRARY_UPDATE_ACTION)) {
            LibraryUpdateService.start(context);
        }

    }

}
