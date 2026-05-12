package eu.kanade.tachiyomi.data.track.yamtrack

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.yamtrack.dto.YTCreateResponse
import eu.kanade.tachiyomi.data.track.yamtrack.dto.YTMediaItem
import eu.kanade.tachiyomi.data.track.yamtrack.dto.YTSearchItem
import eu.kanade.tachiyomi.data.track.yamtrack.dto.YTSearchResponse
import eu.kanade.tachiyomi.data.track.yamtrack.dto.toTrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private fun mediaPath(mediaType: String, source: String, mediaId: String): String =
        "/media/${encodeSegment(mediaType)}/${encodeSegment(source)}/${encodeSegment(mediaId)}/"

    private fun mediaTypeCollectionPath(mediaType: String): String =
        "/media/${encodeSegment(mediaType)}/"

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

    suspend fun search(query: String): List<TrackSearch> = coroutineScope {
        val baseUrl = yamtrack.getBaseUrl().trimEnd('/')
        // Yamtrack's /api/v1/search/{media_type}/ endpoint is scoped to a single media type,
        // so we fan out across the supported types (manga + comic) and merge the results.
        Yamtrack.SUPPORTED_MEDIA_TYPES
            .map { type -> async { searchByMediaType(type, query) } }
            .awaitAll()
            .flatten()
            .map { it.toTrackSearch(yamtrack.id, baseUrl) }
    }

    private suspend fun searchByMediaType(mediaType: String, query: String): List<YTSearchItem> {
        return try {
            val url = apiUrl("/search/${encodeSegment(mediaType)}/").toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("limit", "20")
                .build()

            val response = with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<YTSearchResponse>()
            }
            response.results
                .filter { it.mediaId.isNotBlank() && it.source.isNotBlank() }
                // The search endpoint sometimes omits media_type; backfill from the URL we hit.
                .map { if (it.mediaType.isNullOrBlank()) it.copy(mediaType = mediaType) else it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getMediaItem(mediaType: String, source: String, mediaId: String): YTMediaItem? {
        return try {
            with(json) {
                authClient.newCall(GET(apiUrl(mediaPath(mediaType, source, mediaId))))
                    .awaitSuccess()
                    .parseAs<YTMediaItem>()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addMedia(
        track: Track,
        mediaType: String,
        source: String,
        mediaId: String,
        title: String? = null,
        imageUrl: String? = null,
    ): YTCreateResponse? {
        val body = buildJsonObject {
            put("source", source)
            if (source == Yamtrack.SOURCE_MANUAL) {
                if (!title.isNullOrBlank()) {
                    put("title", title)
                }
                if (!imageUrl.isNullOrBlank()) {
                    put("image", imageUrl)
                }
            } else {
                put("media_id", mediaId)
            }
            putTrackingFields(track)
        }
        val response = authClient.newCall(
            POST(
                url = apiUrl(mediaTypeCollectionPath(mediaType)),
                body = body.toString().toRequestBody(jsonMime),
            ),
        ).awaitSuccess()
        return try {
            with(json) { response.parseAs<YTCreateResponse>() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateMedia(track: Track, mediaType: String, source: String, mediaId: String) {
        val body = buildJsonObject {
            putTrackingFields(track)
        }
        authClient.newCall(
            PATCH(
                url = apiUrl(mediaPath(mediaType, source, mediaId)),
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
        val (source, mediaType, mediaId) = Yamtrack.parseTrackingUrl(track.remoteUrl) ?: return
        authClient.newCall(
            DELETE(url = apiUrl(mediaPath(mediaType, source, mediaId))),
        ).awaitSuccess().close()
    }

    companion object {
        private fun encodeSegment(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    }
}
