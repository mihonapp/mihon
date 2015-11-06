package eu.kanade.mangafeed.data.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DownloadManager;
import eu.kanade.mangafeed.events.DownloadChaptersEvent;
import eu.kanade.mangafeed.util.AndroidComponentUtil;
import eu.kanade.mangafeed.util.ContentObservable;
import eu.kanade.mangafeed.util.EventBusHook;
import rx.Subscription;

public class DownloadService extends Service {

    @Inject DownloadManager downloadManager;

    private Subscription networkChangeSubscription;

    public static void start(Context context) {
        context.startService(new Intent(context, DownloadService.class));
    }

    public static boolean isRunning(Context context) {
        return AndroidComponentUtil.isServiceRunning(context, DownloadService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);
        listenNetworkChanges();

        EventBus.getDefault().registerSticky(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        networkChangeSubscription.unsubscribe();
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
                    // TODO
                });
    }

}
