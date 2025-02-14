@file:Suppress("UNUSED")

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.toDuration
import kotlin.time.toDurationUnit
import mihon.core.network.rateLimit as actualRateLimit

/**
 * An OkHttp interceptor that handles rate limiting.
 *
 * This uses `java.time` APIs and is the legacy method, kept
 * for compatibility reasons with existing extensions.
 *
 * Examples:
 *
 * permits = 5,  period = 1, unit = seconds  =>  5 requests per second
 * permits = 10, period = 2, unit = minutes  =>  10 requests per 2 minutes
 *
 * @since extension-lib 1.3
 *
 * @param permits [Int]   Number of requests allowed within a period of units.
 * @param period [Long]   The limiting duration. Defaults to 1.
 * @param unit [TimeUnit] The unit of time for the period. Defaults to seconds.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = actualRateLimit(permits, period.toDuration(unit.toDurationUnit()))
