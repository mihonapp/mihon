package eu.kanade.mangafeed.data.download.model;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.database.models.Chapter;
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
        download.setStatus(Download.QUEUE);
        queue.add(download);
    }

    public void remove(Download download) {
        queue.remove(download);
        download.setStatus(Download.NOT_DOWNLOADED);
        download.setStatusSubject(null);
    }

    public void remove(Chapter chapter) {
        for (Download download : queue) {
            if (download.chapter.id.equals(chapter.id)) {
                remove(download);
                break;
            }
        }
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
