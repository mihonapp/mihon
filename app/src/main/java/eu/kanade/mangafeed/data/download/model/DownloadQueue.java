package eu.kanade.mangafeed.data.download.model;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.download.model.Download;
import rx.Observable;
import rx.subjects.PublishSubject;

public class DownloadQueue {

    private List<Download> queue;
    private PublishSubject<Download> statusSubject;

    public DownloadQueue() {
        queue = new ArrayList<>();
        statusSubject = PublishSubject.create();
    }

    public void add(Download download) {
        download.setStatusSubject(statusSubject);
        queue.add(download);
    }

    public void remove(Download download) {
        queue.remove(download);
        download.setStatusSubject(null);
    }

    public List<Download> get() {
        return queue;
    }

    public void clearSuccessfulDownloads() {
        for (Download download : queue) {
            if (download.getStatus() == Download.DOWNLOADED) {
                remove(download);
            }
        }
    }

    public Observable<Download> getActiveDownloads() {
        return Observable.from(queue)
                .filter(download -> download.getStatus() == Download.DOWNLOADING);
    }

    public Observable<Download> getStatusObservable() {
        return statusSubject
                .startWith(getActiveDownloads());
    }

}
