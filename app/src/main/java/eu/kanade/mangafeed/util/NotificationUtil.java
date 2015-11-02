package eu.kanade.mangafeed.util;

import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;

import eu.kanade.mangafeed.R;

public class NotificationUtil {

    public static void create(Context context, int nId, String title, String body, int iconRes) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(iconRes == -1 ? R.drawable.ic_action_refresh : iconRes)
                .setContentTitle(title)
                .setContentText(body);


        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(nId, mBuilder.build());
    }

    public static void createBigText(Context context, int nId, String title, String body, int iconRes) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(iconRes == -1 ? R.drawable.ic_action_refresh : iconRes)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body));

        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(nId, mBuilder.build());
    }

    public static void create(Context context, int nId, String title, String body) {
        create(context, nId, title, body, -1);
    }

    public static void createBigText(Context context, int nId, String title, String body) {
        createBigText(context, nId, title, body, -1);
    }

}
