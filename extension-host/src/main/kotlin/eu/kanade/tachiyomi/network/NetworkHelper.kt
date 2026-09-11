package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

open class NetworkHelper(
    open val client: OkHttpClient = createDefaultClient(),
) {
    open val cloudflareClient: OkHttpClient get() = client

    open fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 MihonW/1.0"

        fun createDefaultClient(
            sessionFile: java.io.File? = System.getenv("MIHON_SOURCE_SESSION_FILE")?.let {
                java.io.File(it)
            },
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(2, TimeUnit.MINUTES)
                .addInterceptor(UncaughtExceptionInterceptor())
                // Re-evaluate the destination for each redirect; BridgeInterceptor keeps these
                // network-only headers out of the user request used to construct follow-ups.
                .addNetworkInterceptor(DesktopSessionInterceptor(sessionFile))
                .addInterceptor(UserAgentInterceptor { DEFAULT_USER_AGENT })
                .addInterceptor(CloudflareInterceptor())
                .cookieJar(object : CookieJar {
                    private val store = mutableListOf<Cookie>()

                    @Synchronized
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        store.removeAll { old ->
                            old.expiresAt <= System.currentTimeMillis() || cookies.any {
                                it.name == old.name && it.domain == old.domain && it.path == old.path
                            }
                        }
                        store.addAll(cookies.filter { it.expiresAt > System.currentTimeMillis() })
                    }

                    @Synchronized
                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        store.removeAll { it.expiresAt <= System.currentTimeMillis() }
                        return store.filter { it.matches(url) }.sortedByDescending { it.path.length }
                    }
                })
                .build()
        }
    }
}
