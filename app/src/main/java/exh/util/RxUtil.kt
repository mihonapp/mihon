package exh.util

import rx.Observable
import rx.Single
import rx.subjects.ReplaySubject

/**
 * Transform a cold single to a hot single
 *
 * Note: Behaves like a ReplaySubject
 *       All generated items are buffered in memory!
 */
fun <T> Single<T>.melt(): Single<T> {
    return toObservable().melt().toSingle()
}

/**
 * Transform a cold observable to a hot observable
 *
 * Note: Behaves like a ReplaySubject
 *       All generated items are buffered in memory!
 */
fun <T> Observable<T>.melt(): Observable<T> {
    val rs = ReplaySubject.create<T>()
    subscribe(rs)
    return rs
}