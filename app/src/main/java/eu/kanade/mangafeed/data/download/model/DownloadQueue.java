package eu.kanade.mangafeed.data.download.model;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.source.model.Page;
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
        return statusSubject;
    }

    public Observable<Download> getProgressObservable() {
        return statusSubject
                .startWith(getActiveDownloads())
                .flatMap(download -> {
                    if (download.getStatus() == Download.DOWNLOADING) {
                        PublishSubject<Integer> pageStatusSubject = PublishSubject.create();
                        setPagesSubject(download.pages, pageStatusSubject);
                        return pageStatusSubject
                                .filter(status -> status == Page.READY)
                                .map(status -> download);

                    } else if (download.getStatus() == Download.DOWNLOADED ||
                            download.getStatus() == Download.ERROR) {

                        setPagesSubject(download.pages, null);
                    }
                    return Observable.just(download);
                })
                .filter(download -> download.getStatus() == Download.DOWNLOADING);
    }

    private void setPagesSubject(List<Page> pages, PublishSubject<Integer> subject) {
        if (pages != null) {
            for (Page page : pages) {
                page.setStatusSubject(subject);
            }
        }
    }

}
