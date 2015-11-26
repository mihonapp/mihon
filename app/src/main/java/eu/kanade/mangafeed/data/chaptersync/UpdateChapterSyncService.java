package eu.kanade.mangafeed.data.chaptersync;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.ChapterSync;
import eu.kanade.mangafeed.data.network.NetworkHelper;
import eu.kanade.mangafeed.event.UpdateChapterSyncEvent;
import eu.kanade.mangafeed.util.EventBusHook;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class UpdateChapterSyncService extends Service {

    @Inject ChapterSyncManager syncManager;
    @Inject NetworkHelper networkManager;
    @Inject DatabaseHelper db;

    private CompositeSubscription subscriptions;

    public static void start(Context context) {
        context.startService(new Intent(context, UpdateChapterSyncService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);
        subscriptions = new CompositeSubscription();
        EventBus.getDefault().registerSticky(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        subscriptions.unsubscribe();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @EventBusHook
    public void onEventMainThread(UpdateChapterSyncEvent event) {
        updateLastChapteRead(event.getChapterSync());
    }

    private void updateLastChapteRead(ChapterSync chapterSync) {
        BaseChapterSync sync = syncManager.getSyncService(chapterSync.sync_id);

        subscriptions.add(sync.update(chapterSync)
                .flatMap(response -> db.insertChapterSync(chapterSync).createObservable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    stopSelf();
                }, error -> {
                    stopSelf();
                }));
    }

}
