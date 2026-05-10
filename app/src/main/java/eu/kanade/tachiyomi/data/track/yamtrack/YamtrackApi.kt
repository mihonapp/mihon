package eu.kanade.tachiyomi.data.track.yamtrack

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.yamtrack.dto.YTMediaItem
import eu.kanade.tachiyomi.data.track.yamtrack.dto.YTSearchResponse
import eu.kanade.tachiyomi.data.track.yamtrack.dto.toTrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import tachiyomi.domain.track.model.Track as DomainTrack

class YamtrackApi(
    private val yamtrack: Yamtrack,
    private val baseClient: OkHttpClient,
    interceptor: YamtrackInterceptor,
) {
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val authClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun apiUrl(path: String): String {
        val base = yamtrack.getBaseUrl().trimEnd('/')
        return "$base/api/v1$path"
    }

    private fun mediaPath(source: String, mediaId: String): String =
        "/media/manga/${encodeSegment(source)}/${encodeSegment(mediaId)}/"

    suspend fun verifyCredentials(baseUrl: String, token: String) {
        val url = "${baseUrl.trimEnd('/')}/api/v1/statistics/"
        val authedClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                val authed = YamtrackInterceptor.applyAuthHeaders(chain.request().newBuilder(), token).build()
                chain.proceed(authed)
            }
            .build()
        authedClient.newCall(GET(url)).awaitSuccess().close()
    }

    suspend fun search(query: String): List<TrackSearch> {
        val url = apiUrl("/search/manga/").toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("limit", "20")
            .build()

        val response = with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<YTSearchResponse>()
        }

        val baseUrl = yamtrack.getBaseUrl().trimEnd('/')
        // Return the basic search results immediately. Detail enrichment (synopsis, score,
        // format, start_date) happens lazily via Yamtrack.enrichSearchResults so the search
        // list renders fast instead of waiting on N detail-endpoint round-trips.
        return response.results
            .filter { it.mediaId.isNotBlank() && it.source.isNotBlank() }
            .map { it.toTrackSearch(yamtrack.id, baseUrl) }
    }

    suspend fun getMediaItem(source: String, mediaId: String): YTMediaItem? {
        return try {
            with(json) {
                authClient.newCall(GET(apiUrl(mediaPath(source, mediaId))))
                    .awaitSuccess()
                    .parseAs<YTMediaItem>()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addMedia(track: Track, source: String, mediaId: String, title: String? = null) {
        val body = buildJsonObject {
            put("source", source)
            if (source == Yamtrack.SOURCE_MANUAL) {
                if (!title.isNullOrBlank()) {
                    put("title", title)
                }
            } else {
                put("media_id", mediaId)
            }
            putTrackingFields(track)
        }
        authClient.newCall(
            POST(
                url = apiUrl("/media/manga/"),
                body = body.toString().toRequestBody(jsonMime),
            ),
        ).awaitSuccess().close()
    }

    suspend fun updateMedia(track: Track, source: String, mediaId: String) {
        val body = buildJsonObject {
            putTrackingFields(track)
        }
        authClient.newCall(
            PATCH(
                url = apiUrl(mediaPath(source, mediaId)),
                body = body.toString().toRequestBody(jsonMime),
            ),
        ).awaitSuccess().close()
    }

    private fun JsonObjectBuilder.putTrackingFields(track: Track) {
        put("status", Yamtrack.statusToApi(track.status))
        put("progress", track.last_chapter_read.toInt())
        if (track.score > 0.0) {
            put("score", track.score)
        }
        Yamtrack.formatIsoDate(track.started_reading_date)?.let { put("start_date", it) }
        Yamtrack.formatIsoDate(track.finished_reading_date)?.let { put("end_date", it) }
    }

    suspend fun deleteMedia(track: DomainTrack) {
        val (source, mediaId) = Yamtrack.parseTrackingUrl(track.remoteUrl) ?: return
        authClient.newCall(
            DELETE(url = apiUrl(mediaPath(source, mediaId))),
        ).awaitSuccess().close()
    }

    companion object {
        private fun encodeSegment(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    }
}
