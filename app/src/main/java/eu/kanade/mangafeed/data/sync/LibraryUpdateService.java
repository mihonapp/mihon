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
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.util.AndroidComponentUtil;
import eu.kanade.mangafeed.util.NetworkUtil;
import eu.kanade.mangafeed.util.NotificationUtil;
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class LibraryUpdateService extends Service {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper preferences;

    private Subscription subscription;

    public static final int UPDATE_NOTIFICATION_ID = 1;

    public static void start(Context context) {
        if (!isRunning(context)) {
            context.startService(getStartIntent(context));
        }
    }

    private static Intent getStartIntent(Context context) {
        return new Intent(context, LibraryUpdateService.class);
    }

    private static boolean isRunning(Context context) {
        return AndroidComponentUtil.isServiceRunning(context, LibraryUpdateService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.get(this).getComponent().inject(this);
    }

    @Override
    public void onDestroy() {
        if (subscription != null)
            subscription.unsubscribe();
        // Reset the alarm
        LibraryUpdateAlarm.startAlarm(this);
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

        subscription = Observable.fromCallable(() -> db.getFavoriteMangas().executeAsBlocking())
                .subscribeOn(Schedulers.io())
                .flatMap(this::updateLibrary)
                .subscribe(next -> {},
                        error -> {
                            NotificationUtil.create(this, UPDATE_NOTIFICATION_ID,
                                    getString(R.string.notification_update_error), "");
                            stopSelf(startId);
                        }, () -> {
                            Timber.i("Library updated");
                            stopSelf(startId);
                        });

        return START_STICKY;
    }

    private Observable<MangaUpdate> updateLibrary(List<Manga> allLibraryMangas) {
        final AtomicInteger count = new AtomicInteger(0);
        final List<MangaUpdate> updates = new ArrayList<>();
        final List<Manga> failedUpdates = new ArrayList<>();

        final List<Manga> mangas = !preferences.updateOnlyNonCompleted() ? allLibraryMangas :
            Observable.from(allLibraryMangas)
                    .filter(manga -> manga.status != Manga.COMPLETED)
                    .toList().toBlocking().single();

        return Observable.from(mangas)
                .doOnNext(manga -> NotificationUtil.create(this, UPDATE_NOTIFICATION_ID,
                        getString(R.string.notification_update_progress,
                                count.incrementAndGet(), mangas.size()), manga.title))
                .concatMap(manga -> updateManga(manga)
                        .onErrorReturn(error -> {
                            failedUpdates.add(manga);
                            return new PostResult(0, 0, 0);
                        })
                        .filter(result -> result.getNumberOfRowsInserted() > 0)
                        .map(result -> new MangaUpdate(manga, result)))
                .doOnNext(updates::add)
                .doOnCompleted(() -> NotificationUtil.createBigText(this, UPDATE_NOTIFICATION_ID,
                        getString(R.string.notification_update_completed),
                        getUpdatedMangas(updates, failedUpdates)));
    }

    private Observable<PostResult> updateManga(Manga manga) {
        return sourceManager.get(manga.source)
                .pullChaptersFromNetwork(manga.url)
                .flatMap(chapters -> db.insertOrRemoveChapters(manga, chapters));
    }

    private String getUpdatedMangas(List<MangaUpdate> updates, List<Manga> failedUpdates) {
        final StringBuilder result = new StringBuilder();
        if (updates.isEmpty()) {
            result.append(getString(R.string.notification_no_new_chapters)).append("\n");
        } else {
            result.append(getString(R.string.notification_new_chapters));

            for (MangaUpdate update : updates) {
                result.append("\n").append(update.getManga().title);
            }
        }
        if (!failedUpdates.isEmpty()) {
            result.append("\n");
            result.append(getString(R.string.notification_manga_update_failed));
            for (Manga manga : failedUpdates) {
                result.append("\n").append(manga.title);
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
