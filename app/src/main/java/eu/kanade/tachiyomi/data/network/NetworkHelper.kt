package eu.kanade.tachiyomi.data.network

import android.content.Context
import okhttp3.*
import rx.Observable
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieStore

class NetworkHelper(context: Context) {

    private val client: OkHttpClient
    private val forceCacheClient: OkHttpClient

    private val cookieManager: CookieManager

    private val forceCacheInterceptor = { chain: Interceptor.Chain ->
        val originalResponse = chain.proceed(chain.request())
        originalResponse.newBuilder()
                .removeHeader("Pragma")
                .header("Cache-Control", "max-age=" + 600)
                .build()
    }

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB
    private val cacheDir = "network_cache"

    init {
        val cacheDir = File(context.cacheDir, cacheDir)

        cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)

        client = OkHttpClient.Builder()
                .cookieJar(JavaNetCookieJar(cookieManager))
                .cache(Cache(cacheDir, cacheSize))
                .build()

        forceCacheClient = client.newBuilder()
                .addNetworkInterceptor(forceCacheInterceptor)
                .build()
    }

    @JvmOverloads
    fun request(request: Request, forceCache: Boolean = false): Observable<Response> {
        return Observable.fromCallable {
            val c = if (forceCache) forceCacheClient else client
            c.newCall(request).execute()
        }
    }

    @JvmOverloads
    fun requestBody(request: Request, forceCache: Boolean = false): Observable<String> {
        return request(request, forceCache)
                .map { it.body().string() }
    }

    fun requestBodyProgress(request: Request, listener: ProgressListener): Observable<Response> {
        return Observable.fromCallable {
            val progressClient = client.newBuilder()
                    .cache(null)
                    .addNetworkInterceptor { chain ->
                        val originalResponse = chain.proceed(chain.request())
                        originalResponse.newBuilder()
                                .body(ProgressResponseBody(originalResponse.body(), listener))
                                .build()
                    }
                    .build()

            progressClient.newCall(request).execute()
        }.retry(1)
    }

    val cookies: CookieStore
        get() = cookieManager.cookieStore

}
