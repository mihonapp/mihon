package eu.kanade.tachiyomi.util.lang

import rx.Observable
import rx.Subscription
import rx.subscriptions.CompositeSubscription

operator fun CompositeSubscription.plusAssign(subscription: Subscription) = add(subscription)

fun <T, U, R> Observable<T>.combineLatest(o2: Observable<U>, combineFn: (T, U) -> R): Observable<R> {
    return Observable.combineLatest(this, o2, combineFn)
}
