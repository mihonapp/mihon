package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.Http103Interceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(context: Context) {

    private val preferences: NetworkPreferences by injectLazy()

    private val cacheDir = File(context.cacheDir, "network_cache")
    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    val cookieManager = AndroidCookieJar()

    private val userAgentInterceptor by lazy { UserAgentInterceptor() }
    private val http103Interceptor by lazy { Http103Interceptor(context) }
    private val cloudflareInterceptor by lazy { CloudflareInterceptor(context) }

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder = OkHttpClient.Builder()
                .cookieJar(cookieManager)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(2, TimeUnit.MINUTES)
                .fastFallback(true)
                .addInterceptor(userAgentInterceptor)
                .addNetworkInterceptor(http103Interceptor)

            if (preferences.verboseLogging().get()) {
                val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
                builder.addNetworkInterceptor(httpLoggingInterceptor)
            }

            when (preferences.dohProvider().get()) {
                PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
                PREF_DOH_GOOGLE -> builder.dohGoogle()
                PREF_DOH_ADGUARD -> builder.dohAdGuard()
                PREF_DOH_QUAD9 -> builder.dohQuad9()
                PREF_DOH_ALIDNS -> builder.dohAliDNS()
                PREF_DOH_DNSPOD -> builder.dohDNSPod()
                PREF_DOH_360 -> builder.doh360()
                PREF_DOH_QUAD101 -> builder.dohQuad101()
                PREF_DOH_MULLVAD -> builder.dohMullvad()
                PREF_DOH_CONTROLD -> builder.dohControlD()
                PREF_DOH_NJALLA -> builder.dohNajalla()
            }

            return builder
        }

    val client by lazy { baseClientBuilder.cache(Cache(cacheDir, cacheSize)).build() }

    @Suppress("UNUSED")
    val cloudflareClient by lazy {
        client.newBuilder()
            .addInterceptor(cloudflareInterceptor)
            .build()
    }

    val defaultUserAgent by lazy {
        preferences.defaultUserAgent().get()
    }
}
