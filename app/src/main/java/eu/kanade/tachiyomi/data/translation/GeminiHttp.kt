package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

/**
 * An HTTP client for the Gemini API derived from Mihon's shared client.
 *
 * The shared client logs request headers when "verbose logging" is enabled, which would write the API key to the
 * log, so every header logging interceptor is removed here. Long timeouts: image requests can take a while.
 */
internal fun buildGeminiHttpClient(networkHelper: NetworkHelper): OkHttpClient {
    return networkHelper.client.newBuilder()
        .apply {
            networkInterceptors().removeAll { it.javaClass.name.endsWith("HttpLoggingInterceptor") }
            interceptors().removeAll { it.javaClass.name.endsWith("HttpLoggingInterceptor") }
        }
        .cache(null)
        .connectTimeout(30.seconds)
        .writeTimeout(2.minutes)
        .readTimeout(3.minutes)
        .callTimeout(5.minutes)
        .build()
}

/**
 * OkHttp's "invalid header value" error message echoes the value, which would leak a mistyped key into logs and
 * notifications. Only plain printable ASCII is accepted.
 */
internal fun isValidApiKey(apiKey: String): Boolean {
    val key = apiKey.trim()
    return key.isNotEmpty() && key.all { it.code in 0x21..0x7E }
}
