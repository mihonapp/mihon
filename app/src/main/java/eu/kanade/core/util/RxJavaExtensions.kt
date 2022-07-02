package eu.kanade.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import rx.Emitter
import rx.Observable
import rx.Observer
import kotlin.coroutines.CoroutineContext

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

fun <T : Any> Flow<T>.asObservable(
    context: CoroutineContext = Dispatchers.Unconfined,
    backpressureMode: Emitter.BackpressureMode = Emitter.BackpressureMode.NONE,
): Observable<T> {
    return Observable.create(
        { emitter ->
            /*
             * ATOMIC is used here to provide stable behaviour of subscribe+dispose pair even if
             * asObservable is already invoked from unconfined
             */
            val job = GlobalScope.launch(context = context, start = CoroutineStart.ATOMIC) {
                try {
                    collect { emitter.onNext(it) }
                    emitter.onCompleted()
                } catch (e: Throwable) {
                    // Ignore `CancellationException` as error, since it indicates "normal cancellation"
                    if (e !is CancellationException) {
                        emitter.onError(e)
                    } else {
                        emitter.onCompleted()
                    }
                }
            }
            emitter.setCancellation { job.cancel() }
        },
        backpressureMode,
    )
}
