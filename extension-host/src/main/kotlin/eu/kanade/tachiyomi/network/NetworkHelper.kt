package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

open class NetworkHelper(
    open val client: OkHttpClient = createDefaultClient(),
) {
    open val cloudflareClient: OkHttpClient get() = client

    open fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 MihonW/1.0"

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(2, TimeUnit.MINUTES)
                .addInterceptor(UncaughtExceptionInterceptor())
                .addInterceptor(UserAgentInterceptor { DEFAULT_USER_AGENT })
                .addInterceptor(CloudflareInterceptor())
                .cookieJar(object : CookieJar {
                    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        val list = store.computeIfAbsent(url.host) { mutableListOf() }
                        synchronized(list) {
                            list.removeAll { old -> cookies.any { it.name == old.name } }
                            list.addAll(cookies)
                        }
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        return store[url.host] ?: emptyList()
                    }
                })
                .build()
        }
    }
}
