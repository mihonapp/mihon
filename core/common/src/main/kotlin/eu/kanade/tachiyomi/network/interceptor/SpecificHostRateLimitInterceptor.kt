package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

/**
 * An OkHttp interceptor that handles given url host's rate limiting.
 *
 * This uses Java Time APIs and is the legacy method, kept
 * for compatibility reasons with existing extensions.
 *
 * Examples:
 *
 * httpUrl = "api.manga.com".toHttpUrlOrNull(), permits = 5, period = 1, unit = seconds  =>  5 requests per second to api.manga.com
 * httpUrl = "imagecdn.manga.com".toHttpUrlOrNull(), permits = 10, period = 2, unit = minutes  =>  10 requests per 2 minutes to imagecdn.manga.com
 *
 * @since extension-lib 1.3
 *
 * @param httpUrl [HttpUrl] The url host that this interceptor should handle. Will get url's host by using HttpUrl.host()
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Long]     The limiting duration. Defaults to 1.
 * @param unit [TimeUnit]   The unit of time for the period. Defaults to seconds.
 */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(
    RateLimitInterceptor(httpUrl.host, permits, period.toDuration(unit.toDurationUnit())),
)

/**
 * An OkHttp interceptor that handles given url host's rate limiting.
 *
 * Examples:
 *
 * httpUrl = "https://api.manga.com".toHttpUrlOrNull(), permits = 5, period = 1.seconds =>  5 requests per second to api.manga.com
 * httpUrl = "https://imagecdn.manga.com".toHttpUrlOrNull(), permits = 10, period = 2.minutes  =>  10 requests per 2 minutes to imagecdn.manga.com
 *
 * @since extension-lib 1.5
 *
 * @param httpUrl [HttpUrl] The url host that this interceptor should handle. Will get url's host by using HttpUrl.host()
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
@Suppress("UNUSED")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period))

/**
 * An OkHttp interceptor that handles given url host's rate limiting.
 *
 * Examples:
 *
 * url = "https://api.manga.com", permits = 5, period = 1.seconds =>  5 requests per second to api.manga.com
 * url = "https://imagecdn.manga.com", permits = 10, period = 2.minutes  =>  10 requests per 2 minutes to imagecdn.manga.com
 *
 * @since extension-lib 1.5
 *
 * @param url [String]      The url host that this interceptor should handle. Will get url's host by using HttpUrl.host()
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
@Suppress("UNUSED")
fun OkHttpClient.Builder.rateLimitHost(url: String, permits: Int, period: Duration = 1.seconds) =
    addInterceptor(RateLimitInterceptor(url.toHttpUrlOrNull()?.host, permits, period))
