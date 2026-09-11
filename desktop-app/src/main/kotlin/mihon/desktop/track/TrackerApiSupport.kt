package mihon.desktop.track

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val DEFAULT_TRACKER_TIMEOUT_MILLIS = 15_000L

/** Sentinel used by tracker constructors: keep the timeout configured on the injected client. */
internal const val INHERIT_CLIENT_TIMEOUT_MILLIS = 0L

internal val TRACKER_JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal val defaultTrackerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun defaultTrackerHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(DEFAULT_TRACKER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    .build()

/** Raised for any tracker API/transport failure that is not a plain HTTP status error. */
open class TrackerApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Raised when a tracker API returns a non-successful HTTP status. */
class TrackerHttpException(
    val code: Int,
    val responseBody: String? = null,
) : TrackerApiException(
    buildString {
        append("Tracker API request failed with HTTP ").append(code)
        val body = responseBody?.takeIf { it.isNotBlank() }
        if (body != null) {
            append(": ").append(body.take(500))
        }
    },
)

/** Raised for trackers that are listed in the UI but intentionally not implemented. */
class TrackerNotSupportedException(trackerName: String) :
    UnsupportedOperationException("$trackerName tracking is not supported on Mihon Desktop")

/**
 * Small OkHttp wrapper shared by the real tracker implementations.
 *
 * The client, base URL and timeout are injectable so tests can point trackers at a local
 * [com.sun.net.httpserver.HttpServer] while production keeps sane defaults.
 */
internal class TrackerHttpClient(
    baseUrl: String,
    client: OkHttpClient,
    requestTimeoutMillis: Long = 0L,
) {
    val baseUrl: String = baseUrl.trimEnd('/')

    private val client: OkHttpClient = if (requestTimeoutMillis > 0L) {
        client.newBuilder()
            .callTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    } else {
        // Respect the timeout configured on the injected client (and the 15s default client).
        client
    }

    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw TrackerHttpException(response.code, body)
                }
                body
            }
        } catch (error: TrackerApiException) {
            throw error
        } catch (error: IOException) {
            throw TrackerApiException("Tracker request to ${request.url} failed", error)
        }
    }
}
