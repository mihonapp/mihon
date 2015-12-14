package eu.kanade.mangafeed.data.sync;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.MangaSync;
import eu.kanade.mangafeed.data.mangasync.MangaSyncManager;
import eu.kanade.mangafeed.data.mangasync.base.BaseMangaSync;
import eu.kanade.mangafeed.event.UpdateMangaSyncEvent;
import eu.kanade.mangafeed.util.EventBusHook;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class UpdateMangaSyncService extends Service {

    @Inject MangaSyncManager syncManager;
    @Inject DatabaseHelper db;

    private CompositeSubscription subscriptions;

    public static void start(Context context) {
        context.startService(new Intent(context, UpdateMangaSyncService.class));
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
    public void onEventMainThread(UpdateMangaSyncEvent event) {
        updateLastChapteRead(event.getMangaSync());
    }

    private void updateLastChapteRead(MangaSync mangaSync) {
        BaseMangaSync sync = syncManager.getSyncService(mangaSync.sync_id);

        subscriptions.add(sync.update(mangaSync)
                .flatMap(response -> db.insertMangaSync(mangaSync).createObservable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    stopSelf();
                }, error -> {
                    stopSelf();
                }));
    }

}
