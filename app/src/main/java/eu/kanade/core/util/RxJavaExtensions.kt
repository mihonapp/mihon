package eu.kanade.core.util

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import rx.Observable
import rx.Observer

fun <T : Any> Observable<T>.asFlow(): Flow<T> = callbackFlow {
    val observer = object : Observer<T> {
        override fun onNext(t: T) {
            trySend(t)
        }

        override fun onError(e: Throwable) {
            close(e)
        }

        override fun onCompleted() {
            close()
        }
    }
    val subscription = subscribe(observer)
    awaitClose { subscription.unsubscribe() }
}
