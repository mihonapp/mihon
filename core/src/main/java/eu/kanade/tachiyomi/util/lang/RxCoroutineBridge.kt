package eu.kanade.tachiyomi.util.lang

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Emitter
import rx.Observable
import rx.Subscriber
import rx.Subscription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Util functions for bridging RxJava and coroutines. Taken from TachiyomiEH/SY.
 */

suspend fun <T> Observable<T>.awaitSingle(): T = single().awaitOne()

@OptIn(InternalCoroutinesApi::class)
private suspend fun <T> Observable<T>.awaitOne(): T = suspendCancellableCoroutine { cont ->
    cont.unsubscribeOnCancellation(
        subscribe(
            object : Subscriber<T>() {
                override fun onStart() {
                    request(1)
                }

                override fun onNext(t: T) {
                    cont.resume(t)
                }

                override fun onCompleted() {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "Should have invoked onNext",
                            ),
                        )
                    }
                }

                override fun onError(e: Throwable) {
                    /*
                     * Rx1 observable throws NoSuchElementException if cancellation happened before
                     * element emission. To mitigate this we try to atomically resume continuation with exception:
                     * if resume failed, then we know that continuation successfully cancelled itself
                     */
                    val token = cont.tryResumeWithException(e)
                    if (token != null) {
                        cont.completeResume(token)
                    }
                }
            },
        ),
    )
}

internal fun <T> CancellableContinuation<T>.unsubscribeOnCancellation(sub: Subscription) =
    invokeOnCancellation { sub.unsubscribe() }

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> runAsObservable(
    backpressureMode: Emitter.BackpressureMode = Emitter.BackpressureMode.NONE,
    block: suspend () -> T,
): Observable<T> {
    return Observable.create(
        { emitter ->
            val job = GlobalScope.launch(Dispatchers.Unconfined, start = CoroutineStart.ATOMIC) {
                try {
                    emitter.onNext(block())
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
