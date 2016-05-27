package eu.kanade.tachiyomi.data.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.subscriptions.Subscriptions
import java.io.IOException

fun Call.asObservable(): Observable<Response> {
    return Observable.create { subscriber ->
        subscriber.add(Subscriptions.create { cancel() })

        try {
            val response = execute()
            if (!subscriber.isUnsubscribed) {
                subscriber.onNext(response)
                subscriber.onCompleted()
            }
        } catch (error: IOException) {
            if (!subscriber.isUnsubscribed) {
                subscriber.onError(error)
            }
        }
    }
}

fun OkHttpClient.newCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
            .cache(null)
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                        .body(ProgressResponseBody(originalResponse.body(), listener))
                        .build()
            }
            .build()

    return progressClient.newCall(request)
}