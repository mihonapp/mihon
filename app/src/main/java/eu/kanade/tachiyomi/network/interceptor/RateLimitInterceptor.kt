package eu.kanade.tachiyomi.network.interceptor

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * An OkHttp interceptor that handles rate limiting.
 *
 * Examples:
 *
 * permits = 5,  period = 1, unit = seconds  =>  5 requests per second
 * permits = 10, period = 2, unit = minutes  =>  10 requests per 2 minutes
 *
 * @since extension-lib 1.3
 *
 * @param permits {Int}   Number of requests allowed within a period of units.
 * @param period {Long}   The limiting duration. Defaults to 1.
 * @param unit {TimeUnit} The unit of time for the period. Defaults to seconds.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(RateLimitInterceptor(permits, period, unit))

private class RateLimitInterceptor(
    private val permits: Int,
    period: Long,
    unit: TimeUnit,
) : Interceptor {

    private val requestQueue = ArrayList<Long>(permits)
    private val rateLimitMillis = unit.toMillis(period)

    override fun intercept(chain: Interceptor.Chain): Response {
        // Ignore canceled calls, otherwise they would jam the queue
        if (chain.call().isCanceled()) {
            throw IOException()
        }

        synchronized(requestQueue) {
            val now = SystemClock.elapsedRealtime()
            val waitTime = if (requestQueue.size < permits) {
                0
            } else {
                val oldestReq = requestQueue[0]
                val newestReq = requestQueue[permits - 1]

                if (newestReq - oldestReq > rateLimitMillis) {
                    0
                } else {
                    oldestReq + rateLimitMillis - now // Remaining time
                }
            }

            // Final check
            if (chain.call().isCanceled()) {
                throw IOException()
            }

            if (requestQueue.size == permits) {
                requestQueue.removeAt(0)
            }
            if (waitTime > 0) {
                requestQueue.add(now + waitTime)
                Thread.sleep(waitTime) // Sleep inside synchronized to pause queued requests
            } else {
                requestQueue.add(now)
            }
        }

        return chain.proceed(chain.request())
    }
}
