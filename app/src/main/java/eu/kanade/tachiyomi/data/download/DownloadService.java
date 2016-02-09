package eu.kanade.tachiyomi.data.download;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import javax.inject.Inject;

import eu.kanade.tachiyomi.App;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.event.DownloadChaptersEvent;
import eu.kanade.tachiyomi.util.ToastUtil;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class DownloadService extends Service {

    @Inject DownloadManager downloadManager;
    @Inject PreferencesHelper preferences;

    private PowerManager.WakeLock wakeLock;
    private Subscription networkChangeSubscription;
    private Subscription queueRunningSubscription;
    private boolean isRunning;

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
        EventBus.getDefault().register(this);
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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(DownloadChaptersEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        downloadManager.onDownloadChaptersEvent(event);
    }

    private void listenNetworkChanges() {
        networkChangeSubscription = new ReactiveNetwork().enableInternetCheck()
                .observeConnectivity(getApplicationContext())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    switch (state) {
                        case WIFI_CONNECTED_HAS_INTERNET:
                            // If there are no remaining downloads, destroy the service
                            if (!isRunning && !downloadManager.startDownloads()) {
                                stopSelf();
                            }
                            break;
                        case MOBILE_CONNECTED:
                            if (!preferences.downloadOnlyOverWifi()) {
                                if (!isRunning && !downloadManager.startDownloads()) {
                                    stopSelf();
                                }
                            } else if (isRunning) {
                                downloadManager.stopDownloads();
                            }
                            break;
                        default:
                            if (isRunning) {
                                downloadManager.stopDownloads();
                            }
                            break;
                    }
                }, error -> {
                    ToastUtil.showShort(this, R.string.download_queue_error);
                    stopSelf();
                });
    }

    private void listenQueueRunningChanges() {
        queueRunningSubscription = downloadManager.getRunningSubject()
                .subscribe(running -> {
                    isRunning = running;
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
