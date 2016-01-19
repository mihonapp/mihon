package eu.kanade.tachiyomi.data.sync;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import javax.inject.Inject;

import eu.kanade.tachiyomi.App;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.MangaSync;
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager;
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class UpdateMangaSyncService extends Service {

    @Inject MangaSyncManager syncManager;
    @Inject DatabaseHelper db;

    private CompositeSubscription subscriptions;

    private static final String EXTRA_MANGASYNC = "extra_mangasync";

    public static void start(Context context, MangaSync mangaSync) {
        Intent intent = new Intent(context, UpdateMangaSyncService.class);
        intent.putExtra(EXTRA_MANGASYNC, mangaSync);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);
        subscriptions = new CompositeSubscription();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MangaSync mangaSync = (MangaSync) intent.getSerializableExtra(EXTRA_MANGASYNC);
        updateLastChapterRead(mangaSync, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        subscriptions.unsubscribe();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateLastChapterRead(MangaSync mangaSync, int startId) {
        MangaSyncService sync = syncManager.getSyncService(mangaSync.sync_id);

        subscriptions.add(Observable.defer(() -> sync.update(mangaSync))
                .flatMap(response -> {
                    if (response.isSuccessful()) {
                        return db.insertMangaSync(mangaSync).asRxObservable();
                    }
                    return Observable.error(new Exception("Could not update MAL"));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    stopSelf(startId);
                }, error -> {
                    stopSelf(startId);
                }));
    }

}
