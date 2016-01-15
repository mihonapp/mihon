package eu.kanade.tachiyomi.data.sync;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import eu.kanade.tachiyomi.App;
import eu.kanade.tachiyomi.BuildConfig;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.SourceManager;
import eu.kanade.tachiyomi.ui.main.MainActivity;
import eu.kanade.tachiyomi.util.AndroidComponentUtil;
import eu.kanade.tachiyomi.util.NetworkUtil;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class LibraryUpdateService extends Service {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper preferences;

    private PowerManager.WakeLock wakeLock;
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
        createAndAcquireWakeLock();
    }

    @Override
    public void onDestroy() {
        if (subscription != null)
            subscription.unsubscribe();
        // Reset the alarm
        LibraryUpdateAlarm.startAlarm(this);
        destroyWakeLock();
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
                            showNotification(getString(R.string.notification_update_error), "");
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
                .doOnNext(manga -> showNotification(
                        getString(R.string.notification_update_progress,
                                count.incrementAndGet(), mangas.size()), manga.title))
                .concatMap(manga -> updateManga(manga)
                        .onErrorReturn(error -> {
                            failedUpdates.add(manga);
                            return Pair.create(0, 0);
                        })
                        // Filter out mangas without new chapters
                        .filter(pair -> pair.first > 0)
                        .map(pair -> new MangaUpdate(manga, pair.first)))
                .doOnNext(updates::add)
                .doOnCompleted(() -> showBigNotification(getString(R.string.notification_update_completed),
                        getUpdatedMangas(updates, failedUpdates)));
    }

    private Observable<Pair<Integer, Integer>> updateManga(Manga manga) {
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
                result.append("\n").append(update.manga.title);
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

    private void createAndAcquireWakeLock() {
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "LibraryUpdateService:WakeLock");
        wakeLock.acquire();
    }

    private void destroyWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void showNotification(String title, String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(title)
                .setContentText(body);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build());
    }

    private void showBigNotification(String title, String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(getNotificationIntent())
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build());
    }

    private PendingIntent getNotificationIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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
        public Manga manga;
        public int newChapters;

        public MangaUpdate(Manga manga, int newChapters) {
            this.manga = manga;
            this.newChapters = newChapters;
        }
    }

}
