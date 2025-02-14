package mihon.core.network

import android.os.SystemClock
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Semaphore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * An OkHttp interceptor that enforces rate limiting across all requests.
 *
 * Examples:
 *
 * permits = 5,  period = 1.seconds  =>  5 requests per second
 * permits = 10, period = 2.minutes  =>  10 requests per 2 minutes
 *
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = rateLimit(permits, period) { true }

/**
 * An OkHttp interceptor that enforces conditional rate limiting based on a given condition.
 *
 * Examples:
 *
 * permits = 5, period = 1.seconds, shouldLimit = { it.host == "api.manga.example" } => 5 requests per second to api.manga.example.
 * permits = 10, period = 2.minutes, shouldLimit = { it.encodedPath.startsWith("/images/") } => 10 requests per 2 minutes to paths starting with "/images/".
 *
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 * @param shouldLimit       A predicate to determine whether the rate limit should apply to a given request.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
    shouldLimit: (HttpUrl) -> Boolean,
): OkHttpClient.Builder = addInterceptor(RateLimitInterceptor(permits, period, shouldLimit))

/** We can probably accept domains or wildcards by comparing with [endsWith], etc. */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal class RateLimitInterceptor(
    private val permits: Int,
    period: Duration,
    private val shouldLimit: (HttpUrl) -> Boolean,
) : Interceptor {

    private val requestQueue = ArrayDeque<Long>(permits)
    private val rateLimitMillis = period.inWholeMilliseconds
    private val fairLock = Semaphore(1, true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        if (call.isCanceled()) throw IOException("Canceled")

        val request = chain.request()
        if (!shouldLimit(request.url)) return chain.proceed(request)

        try {
            fairLock.acquire()
        } catch (e: InterruptedException) {
            throw IOException(e)
        }

        val requestQueue = this.requestQueue
        val timestamp: Long

        try {
            synchronized(requestQueue) {
                while (requestQueue.size >= permits) { // queue is full, remove expired entries
                    val periodStart = SystemClock.elapsedRealtime() - rateLimitMillis
                    var hasRemovedExpired = false
                    while (!requestQueue.isEmpty() && requestQueue.first <= periodStart) {
                        requestQueue.removeFirst()
                        hasRemovedExpired = true
                    }
                    if (call.isCanceled()) {
                        throw IOException("Canceled")
                    } else if (hasRemovedExpired) {
                        break
                    } else {
                        try { // wait for the first entry to expire, or notified by cached response
                            (requestQueue as Object).wait(requestQueue.first - periodStart)
                        } catch (_: InterruptedException) {
                            continue
                        }
                    }
                }

                // add request to queue
                timestamp = SystemClock.elapsedRealtime()
                requestQueue.addLast(timestamp)
            }
        } finally {
            fairLock.release()
        }

        val response = chain.proceed(request)
        if (response.networkResponse == null) { // response is cached, remove it from queue
            synchronized(requestQueue) {
                if (requestQueue.isEmpty() || timestamp < requestQueue.first) return@synchronized
                requestQueue.removeFirstOccurrence(timestamp)
                (requestQueue as Object).notifyAll()
            }
        }

        return response
    }
}
