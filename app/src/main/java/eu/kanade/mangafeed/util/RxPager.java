package eu.kanade.mangafeed.util;

import rx.Observable;
import rx.subjects.PublishSubject;

public class RxPager {

    private final int initialPageCount;
    private final PublishSubject<Integer> requests = PublishSubject.create();
    private int requestedCount;

    public RxPager() {
        this(1);
    }

    public RxPager(int initialPageCount) {
        this.initialPageCount = initialPageCount;
    }

    public void requestNext(int page) {
        requests.onNext(page);
    }

    public Observable<Integer> pages() {
        return requests
            .concatMap(targetPage -> targetPage <= requestedCount ?
                    Observable.<Integer>never() :
                    Observable.range(requestedCount, targetPage - requestedCount))
            .startWith(Observable.range(0, initialPageCount))
            .doOnNext(it -> requestedCount = it + 1);
    }
}