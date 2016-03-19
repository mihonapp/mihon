package eu.kanade.tachiyomi.util

import android.util.Pair
import rx.Observable
import rx.subjects.PublishSubject

class RxPager<T> {

    private val results = PublishSubject.create<List<T>>()
    private var requestedCount: Int = 0

    fun results(): Observable<Pair<Int, List<T>>> {
        requestedCount = 0
        return results.map { Pair(requestedCount++, it) }
    }

    fun request(networkObservable: (Int) -> Observable<List<T>>) =
        networkObservable(requestedCount).doOnNext { results.onNext(it) }

}

