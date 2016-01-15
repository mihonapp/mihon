package eu.kanade.tachiyomi.util;

import android.content.Context;
import android.widget.Toast;

public class ToastUtil {

    public static void showShort(Context context, int resourceId) {
        Toast.makeText(context, resourceId, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(Context context, int resourceId) {
        Toast.makeText(context, resourceId, Toast.LENGTH_LONG).show();
    }

    public static void showShort(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

}
