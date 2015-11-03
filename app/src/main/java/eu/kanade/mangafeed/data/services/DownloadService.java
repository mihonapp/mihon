package eu.kanade.mangafeed.data.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DownloadManager;
import eu.kanade.mangafeed.events.DownloadChapterEvent;
import eu.kanade.mangafeed.util.AndroidComponentUtil;
import eu.kanade.mangafeed.util.EventBusHook;

public class DownloadService extends Service {

    @Inject DownloadManager downloadManager;

    public static Intent getStartIntent(Context context) {
        return new Intent(context, DownloadService.class);
    }

    public static boolean isRunning(Context context) {
        return AndroidComponentUtil.isServiceRunning(context, DownloadService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);

        EventBus.getDefault().register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @EventBusHook
    public void onEvent(DownloadChapterEvent event) {
        downloadManager.getDownloadsSubject().onNext(event);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

}
