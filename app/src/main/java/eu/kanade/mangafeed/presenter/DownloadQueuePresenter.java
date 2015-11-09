package eu.kanade.mangafeed.presenter;

import android.os.Bundle;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DownloadManager;
import eu.kanade.mangafeed.data.models.Download;
import eu.kanade.mangafeed.data.models.DownloadQueue;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.fragment.DownloadQueueFragment;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class DownloadQueuePresenter extends BasePresenter<DownloadQueueFragment> {

    @Inject DownloadManager downloadManager;

    private DownloadQueue downloadQueue;
    private Subscription statusSubscription;
    private HashMap<Download, Subscription> progressSubscriptions;
    private HashMap<Download, Subscription> pageStatusSubscriptions;

    public final static int GET_DOWNLOAD_QUEUE = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        downloadQueue = downloadManager.getQueue();
        progressSubscriptions = new HashMap<>();
        pageStatusSubscriptions = new HashMap<>();

        restartableLatestCache(GET_DOWNLOAD_QUEUE,
                () -> Observable.just(downloadQueue.get()),
                DownloadQueueFragment::onNextDownloads,
                (view, error) -> Timber.e(error.getMessage()));

        if (savedState == null)
            start(GET_DOWNLOAD_QUEUE);
    }

    @Override
    protected void onTakeView(DownloadQueueFragment view) {
        super.onTakeView(view);

        add(statusSubscription = downloadQueue.getStatusObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(download -> {
                    processStatus(download, view);
                }));
    }

    @Override
    protected void onDropView() {
        destroySubscriptions();
        super.onDropView();
    }

    private void processStatus(Download download, DownloadQueueFragment view) {
        switch (download.getStatus()) {
            case Download.DOWNLOADING:
                observeProgress(download, view);
                observePagesStatus(download, view);
                break;
            case Download.DOWNLOADED:
                unsubscribeProgress(download);
                unsubscribePagesStatus(download);
                view.updateProgress(download);
                view.updateDownloadedPages(download);
                break;
            case Download.ERROR:
                unsubscribeProgress(download);
                unsubscribePagesStatus(download);
                break;
        }
    }

    private void observeProgress(Download download, DownloadQueueFragment view) {
        Subscription subscription = Observable.interval(50, TimeUnit.MILLISECONDS, Schedulers.newThread())
                .flatMap(tick -> Observable.from(download.pages)
                        .map(Page::getProgress)
                        .reduce((x, y) -> x + y))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(progress -> {
                    if (download.totalProgress != progress) {
                        download.totalProgress = progress;
                        view.updateProgress(download);
                    }
                });

        // Avoid leaking subscriptions
        Subscription oldSubscription = progressSubscriptions.remove(download);
        if (oldSubscription != null) oldSubscription.unsubscribe();

        progressSubscriptions.put(download, subscription);
    }

    private void observePagesStatus(Download download, DownloadQueueFragment view) {
        PublishSubject<Integer> pageStatusSubject = PublishSubject.create();
        for (Page page : download.pages) {
            if (page.getStatus() != Page.READY)
                page.setStatusSubject(pageStatusSubject);
        }

        Subscription subscription = pageStatusSubject
                .filter(status -> status == Page.READY)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    view.updateDownloadedPages(download);
                });

        // Avoid leaking subscriptions
        Subscription oldSubscription = progressSubscriptions.remove(download);
        if (oldSubscription != null) oldSubscription.unsubscribe();

        pageStatusSubscriptions.put(download, subscription);
    }

    private void unsubscribeProgress(Download download) {
        Subscription subscription = progressSubscriptions.remove(download);
        if (subscription != null)
            subscription.unsubscribe();
    }

    private void unsubscribePagesStatus(Download download) {
        if (download.pages != null) {
            for (Page page : download.pages)
                page.setStatusSubject(null);
        }

        Subscription subscription = pageStatusSubscriptions.remove(download);
        if (subscription != null)
            subscription.unsubscribe();
    }

    private void destroySubscriptions() {
        for (Download download : pageStatusSubscriptions.keySet()) {
            for (Page page : download.pages)
                page.setStatusSubject(null);
        }
        for (Subscription subscription : pageStatusSubscriptions.values()) {
            subscription.unsubscribe();
        }
        pageStatusSubscriptions.clear();

        for (Subscription subscription : progressSubscriptions.values()) {
            subscription.unsubscribe();
        }
        progressSubscriptions.clear();

        remove(statusSubscription);
    }

}
