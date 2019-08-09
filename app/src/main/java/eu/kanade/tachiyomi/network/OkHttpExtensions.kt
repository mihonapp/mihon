package eu.kanade.tachiyomi.network

import exh.util.withRootCause
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.util.concurrent.atomic.AtomicBoolean

fun Call.asObservableWithAsyncStacktrace(): Observable<Pair<Exception, Response>> {
    // Record stacktrace at creation time for easier debugging
    //   asObservable is involved in a lot of crashes so this is worth the performance hit
    val asyncStackTrace = Exception("Async stacktrace")

    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter = object : AtomicBoolean(), Producer, Subscription {
            val executed = AtomicBoolean(false)

            override fun request(n: Long) {
                if (n == 0L || !compareAndSet(false, true)) return

                try {
                    val response = call.execute()
                    executed.set(true)
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(asyncStackTrace to response)
                        subscriber.onCompleted()
                    }
                } catch (error: Throwable) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(error.withRootCause(asyncStackTrace))
                    }
                }
            }

            override fun unsubscribe() {
                if(!executed.get())
                    call.cancel()
            }

            override fun isUnsubscribed(): Boolean {
                return call.isCanceled
            }
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

fun Call.asObservable() = asObservableWithAsyncStacktrace().map { it.second }

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservableWithAsyncStacktrace().map { (asyncStacktrace, response) ->
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP error ${response.code()}", asyncStacktrace)
        } else response
    }
}

fun OkHttpClient.newCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
            .cache(null)
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                        .body(ProgressResponseBody(originalResponse.body()!!, listener))
                        .build()
            }
            .build()

    return progressClient.newCall(request)
}