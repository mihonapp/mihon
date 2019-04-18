package exh.util

import com.pushtorefresh.storio.operations.PreparedOperation
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.*
import rx.subjects.ReplaySubject
import kotlin.coroutines.resumeWithException

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

suspend fun <T> Single<T>.await(subscribeOn: Scheduler? = null): T {
    return suspendCancellableCoroutine { continuation ->
        val self = if (subscribeOn != null) subscribeOn(subscribeOn) else this
        lateinit var sub: Subscription
        sub = self.subscribe({
            continuation.resume(it) {
                sub.unsubscribe()
            }
        }, {
            if (!continuation.isCancelled)
                continuation.resumeWithException(it)
        })

        continuation.invokeOnCancellation {
            sub.unsubscribe()
        }
    }
}

suspend fun <T> PreparedOperation<T>.await(): T = asRxSingle().await()
suspend fun <T> PreparedGetObject<T>.await(): T? = asRxSingle().await()

suspend fun Completable.awaitSuspending(subscribeOn: Scheduler? = null) {
    return suspendCancellableCoroutine { continuation ->
        val self = if (subscribeOn != null) subscribeOn(subscribeOn) else this
        lateinit var sub: Subscription
        sub = self.subscribe({
            continuation.resume(Unit) {
                sub.unsubscribe()
            }
        }, {
            if (!continuation.isCancelled)
                continuation.resumeWithException(it)
        })

        continuation.invokeOnCancellation {
            sub.unsubscribe()
        }
    }
}
