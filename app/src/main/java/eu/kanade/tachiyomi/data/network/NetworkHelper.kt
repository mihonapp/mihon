package eu.kanade.tachiyomi.data.network

import android.content.Context
import okhttp3.*
import rx.Observable
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieStore

class NetworkHelper(context: Context) {

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val forceCacheInterceptor = { chain: Interceptor.Chain ->
        val originalResponse = chain.proceed(chain.request())
        originalResponse.newBuilder()
                .removeHeader("Pragma")
                .header("Cache-Control", "max-age=" + 600)
                .build()
    }

    private val client = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .cache(Cache(cacheDir, cacheSize))
            .build()

    private val forceCacheClient = client.newBuilder()
            .addNetworkInterceptor(forceCacheInterceptor)
            .build()

    val cookies: CookieStore
        get() = cookieManager.cookieStore

    @JvmOverloads
    fun request(request: Request, forceCache: Boolean = false): Observable<Response> {
        return Observable.fromCallable {
            val c = if (forceCache) forceCacheClient else client
            c.newCall(request).execute().apply { body().close() }
        }
    }

    @JvmOverloads
    fun requestBody(request: Request, forceCache: Boolean = false): Observable<String> {
        return Observable.fromCallable {
            val c = if (forceCache) forceCacheClient else client
            c.newCall(request).execute().body().string()
        }
    }

    fun requestBodyProgress(request: Request, listener: ProgressListener): Observable<Response> {
        return Observable.fromCallable { requestBodyProgressBlocking(request, listener) }
    }

    fun requestBodyProgressBlocking(request: Request, listener: ProgressListener): Response {
        val progressClient = client.newBuilder()
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
