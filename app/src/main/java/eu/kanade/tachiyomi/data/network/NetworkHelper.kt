package eu.kanade.tachiyomi.data.network

import android.content.Context
import okhttp3.*
import rx.Observable
import rx.subscriptions.Subscriptions
import timber.log.Timber
import java.io.File
import java.io.IOException

class NetworkHelper(context: Context) {

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    private val cookieManager = PersistentCookieJar(context)

    val defaultClient = OkHttpClient.Builder()
            .cookieJar(cookieManager)
            .cache(Cache(cacheDir, cacheSize))
            .build()

    val forceCacheClient = defaultClient.newBuilder()
            .addNetworkInterceptor({ chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "max-age=" + 600)
                        .build()
            })
            .build()

    val cloudflareClient = defaultClient.newBuilder()
            .addInterceptor(CloudflareInterceptor(cookies))
            .build()

    val cookies: PersistentCookieStore
        get() = cookieManager.store

    fun request(request: Request, client: OkHttpClient = defaultClient): Observable<Response> {
        return Observable.create { subscriber ->
            val call = client.newCall(request)
            subscriber.add(Subscriptions.create {
                call.cancel()
                Timber.i("Cancel call on thread ${Thread.currentThread().id}")
            })

            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.add(Subscriptions.create {
                            response.body().close()
                            Timber.i("Close body on thread ${Thread.currentThread().id}")
                        })
                        subscriber.onNext(response)
                        Timber.i("Emit response on thread ${Thread.currentThread().id}")
                        subscriber.onCompleted()
                    }
                }

                override fun onFailure(call: Call, error: IOException) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(error)
                    }
                }
            })
        }
    }

    fun requestBodyProgress(request: Request, listener: ProgressListener): Observable<Response> {
        return Observable.fromCallable { requestBodyProgressBlocking(request, listener) }
    }

    fun requestBodyProgressBlocking(request: Request, listener: ProgressListener): Response {
        val progressClient = defaultClient.newBuilder()
                .cache(null)
                .addNetworkInterceptor { chain ->
                    val originalResponse = chain.proceed(chain.request())
                    originalResponse.newBuilder()
                            .body(ProgressResponseBody(originalResponse.body(), listener))
                            .build()
                }
                .build()

        return progressClient.newCall(request).execute()
    }

}
