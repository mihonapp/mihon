package eu.kanade.tachiyomi.data.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.File

class NetworkHelper(context: Context) {

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    private val cookieManager = PersistentCookieJar(context)

    val client = OkHttpClient.Builder()
            .cookieJar(cookieManager)
            .cache(Cache(cacheDir, cacheSize))
            .build()

    val forceCacheClient = client.newBuilder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "max-age=600")
                        .build()
            }
            .build()

    val cloudflareClient = client.newBuilder()
            .addInterceptor(CloudflareInterceptor(cookies))
            .build()

    val cookies: PersistentCookieStore
        get() = cookieManager.store

    fun request(request: Request, client: OkHttpClient = this.client): Observable<Response> {
        return client.newCall(request).asObservable()
    }

}
