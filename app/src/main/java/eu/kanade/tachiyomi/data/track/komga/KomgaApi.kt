package eu.kanade.tachiyomi.data.track.komga

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.injectLazy

private const val READLIST_API = "/api/v1/readlists"

class KomgaApi(
    private val trackId: Long,
    private val client: OkHttpClient,
) {

    private val json: Json by injectLazy()

    suspend fun getTrackSearch(url: String): TrackSearch =
        withIOContext {
            try {
                val track = with(json) {
                    if (url.contains(READLIST_API)) {
                        client.newCall(GET(url))
                            .awaitSuccess()
                            .parseAs<ReadListDto>()
                            .toTrack()
                    } else {
                        client.newCall(GET(url))
                            .awaitSuccess()
                            .parseAs<SeriesDto>()
                            .toTrack()
                    }
                }

                val progress = client
                    .newCall(GET("${url.replace("/api/v1/series/", "/api/v2/series/")}/read-progress/tachiyomi"))
                    .awaitSuccess().let {
                        with(json) {
                            if (url.contains("/api/v1/series/")) {
                                it.parseAs<ReadProgressV2Dto>()
                            } else {
                                it.parseAs<ReadProgressDto>().toV2()
                            }
                        }
                    }

                track.apply {
                    cover_url = "$url/thumbnail"
                    tracking_url = url
                    total_chapters = progress.maxNumberSort.toInt()
                    status = when (progress.booksCount) {
                        progress.booksUnreadCount -> Komga.UNREAD
                        progress.booksReadCount -> Komga.COMPLETED
                        else -> Komga.READING
                    }
                    last_chapter_read = progress.lastReadContinuousNumberSort
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not get item: $url" }
                throw e
            }
        }

    suspend fun updateProgress(track: Track): Track {
        val payload = if (track.tracking_url.contains("/api/v1/series/")) {
            json.encodeToString(ReadProgressUpdateV2Dto(track.last_chapter_read))
        } else {
            json.encodeToString(ReadProgressUpdateDto(track.last_chapter_read.toInt()))
        }
        client.newCall(
            Request.Builder()
                .url("${track.tracking_url.replace("/api/v1/series/", "/api/v2/series/")}/read-progress/tachiyomi")
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build(),
        )
            .awaitSuccess()
        return getTrackSearch(track.tracking_url)
    }

    private fun SeriesDto.toTrack(): TrackSearch = TrackSearch.create(trackId).also {
        it.title = metadata.title
        it.summary = metadata.summary
        it.publishing_status = metadata.status
    }

    private fun ReadListDto.toTrack(): TrackSearch = TrackSearch.create(trackId).also {
        it.title = name
    }
}
