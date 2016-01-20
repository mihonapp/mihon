package eu.kanade.tachiyomi.util;

import android.util.Pair;

import java.util.List;

import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

public class RxPager<T> {

    private final PublishSubject<List<T>> results = PublishSubject.create();
    private int requestedCount;

    public Observable<Pair<Integer, List<T>>> results() {
        requestedCount = 0;
        return results.map(list -> Pair.create(requestedCount++, list));
    }

    public Observable<List<T>> request(Func1<Integer, Observable<List<T>>> networkObservable) {
        return networkObservable.call(requestedCount).doOnNext(results::onNext);
    }

}

