package eu.kanade.mangafeed.data.download;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.os.PowerManager;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.event.DownloadChaptersEvent;
import eu.kanade.mangafeed.util.ContentObservable;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.util.NetworkUtil;
import rx.Subscription;

public class DownloadService extends Service {

    @Inject DownloadManager downloadManager;

    private PowerManager.WakeLock wakeLock;
    private Subscription networkChangeSubscription;
    private Subscription queueRunningSubscription;

    public static void start(Context context) {
        context.startService(new Intent(context, DownloadService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, DownloadService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);

        createWakeLock();

        listenQueueRunningChanges();
        EventBus.getDefault().registerSticky(this);
        listenNetworkChanges();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        queueRunningSubscription.unsubscribe();
        networkChangeSubscription.unsubscribe();
        downloadManager.destroySubscriptions();
        destroyWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @EventBusHook
    public void onEvent(DownloadChaptersEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        downloadManager.onDownloadChaptersEvent(event);
    }

    private void listenNetworkChanges() {
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        networkChangeSubscription = ContentObservable.fromBroadcast(this, intentFilter)
                .subscribe(state -> {
                    if (NetworkUtil.isNetworkConnected(this)) {
                        // If there are no remaining downloads, destroy the service
                        if (!downloadManager.startDownloads())
                            stopSelf();
                    } else {
                        downloadManager.stopDownloads();
                    }
                });
    }

    private void listenQueueRunningChanges() {
        queueRunningSubscription = downloadManager.getRunningSubject()
                .subscribe(running -> {
                    if (running)
                        acquireWakeLock();
                    else
                        releaseWakeLock();
                });
    }

    private void createWakeLock() {
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "DownloadService:WakeLock");
    }

    private void destroyWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    public void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    public void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

}
