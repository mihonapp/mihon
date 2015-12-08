package eu.kanade.mangafeed.data.sync;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.BuildConfig;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.util.AndroidComponentUtil;
import eu.kanade.mangafeed.util.NetworkUtil;
import eu.kanade.mangafeed.util.NotificationUtil;
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;
import rx.Subscription;
import timber.log.Timber;

public class LibraryUpdateService extends Service {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;

    private Subscription updateSubscription;

    public static final int UPDATE_NOTIFICATION_ID = 1;

    public static Intent getStartIntent(Context context) {
        return new Intent(context, LibraryUpdateService.class);
    }

    public static boolean isRunning(Context context) {
        return AndroidComponentUtil.isServiceRunning(context, LibraryUpdateService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);
    }

    @Override
    public void onDestroy() {
        if (updateSubscription != null)
            updateSubscription.unsubscribe();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        Timber.i("Starting sync...");

        if (!NetworkUtil.isNetworkConnected(this)) {
            Timber.i("Sync canceled, connection not available");
            AndroidComponentUtil.toggleComponent(this, SyncOnConnectionAvailable.class, true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        Observable.fromCallable(() -> db.getFavoriteMangas().executeAsBlocking())
                .subscribe(mangas -> {
                    startUpdating(mangas, startId);
                });

        return START_STICKY;
    }

    private void startUpdating(final List<Manga> mangas, final int startId) {
        if (updateSubscription != null && !updateSubscription.isUnsubscribed())
            updateSubscription.unsubscribe();

        final AtomicInteger count = new AtomicInteger(0);

        List<MangaUpdate> updates = new ArrayList<>();

        updateSubscription = Observable.from(mangas)
                .doOnNext(manga -> {
                    NotificationUtil.create(this, UPDATE_NOTIFICATION_ID,
                            getString(R.string.notification_progress, count.incrementAndGet(), mangas.size()),
                            manga.title);
                })
                .concatMap(manga -> sourceManager.get(manga.source)
                                .pullChaptersFromNetwork(manga.url)
                                .flatMap(chapters -> db.insertOrRemoveChapters(manga, chapters))
                                .filter(result -> result.getNumberOfRowsInserted() > 0)
                                .flatMap(result -> Observable.just(new MangaUpdate(manga, result)))
                )
                .subscribe(update -> {
                    updates.add(update);
                }, error -> {
                    Timber.e("Error syncing");
                    stopSelf(startId);
                }, () -> {
                    NotificationUtil.createBigText(this, UPDATE_NOTIFICATION_ID,
                            getString(R.string.notification_completed), getUpdatedMangas(updates));
                    stopSelf(startId);
                });
    }

    private String getUpdatedMangas(List<MangaUpdate> updates) {
        final StringBuilder result = new StringBuilder();
        if (updates.isEmpty()) {
            result.append(getString(R.string.notification_no_new_chapters)).append("\n");
        } else {
            result.append(getString(R.string.notification_new_chapters));

            for (MangaUpdate update : updates) {
                result.append("\n").append(update.getManga().title);
            }
        }

        return result.toString();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class SyncOnConnectionAvailable extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (NetworkUtil.isNetworkConnected(context)) {
                if (BuildConfig.DEBUG) {
                    Timber.i("Connection is now available, triggering sync...");
                }
                AndroidComponentUtil.toggleComponent(context, this.getClass(), false);
                context.startService(getStartIntent(context));
            }
        }
    }

    private static class MangaUpdate {
        private Manga manga;
        private PostResult result;

        public MangaUpdate(Manga manga, PostResult result) {
            this.manga = manga;
            this.result = result;
        }

        public Manga getManga() {
            return manga;
        }

        public PostResult getResult() {
            return result;
        }
    }

}
