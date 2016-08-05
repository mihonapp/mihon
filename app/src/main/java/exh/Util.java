package exh;

/**
 * Project: tachiyomi
 * Created: 19/04/16
 */
public class Util {
    public static void d(String TAG, String message) {
        int maxLogSize = 1000;
        for(int i = 0; i <= message.length() / maxLogSize; i++) {
            int start = i * maxLogSize;
            int end = (i+1) * maxLogSize;
            end = end > message.length() ? message.length() : end;
            android.util.Log.d(TAG, message.substring(start, end));
        }
    }
}
