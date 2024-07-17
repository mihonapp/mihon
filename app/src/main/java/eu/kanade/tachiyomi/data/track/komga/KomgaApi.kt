package eu.kanade.tachiyomi.data.track.komga

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

private const val READLIST_API = "/api/v1/readlists"

class KomgaApi(
    private val trackId: Long,
    private val client: OkHttpClient,
) {

    private val headers: Headers by lazy {
        Headers.Builder()
            .add("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()
    }

    private val json: Json by injectLazy()

    suspend fun getTrackSearch(url: String): TrackSearch =
        withIOContext {
            try {
                val track = with(json) {
                    if (url.contains(READLIST_API)) {
                        client.newCall(GET(url, headers))
                            .awaitSuccess()
                            .parseAs<ReadListDto>()
                            .toTrack()
                    } else {
                        client.newCall(GET(url, headers))
                            .awaitSuccess()
                            .parseAs<SeriesDto>()
                            .toTrack()
                    }
                }

                val progress = client
                    .newCall(
                        GET("${url.replace("/api/v1/series/", "/api/v2/series/")}/read-progress/tachiyomi", headers),
                    )
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
                    total_chapters = progress.maxNumberSort.toLong()
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
                .headers(headers)
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

    internal suspend fun getBookInfo(chapter: SChapter):BookDtoPartial {
        with(json){
            return client.newCall(GET(chapter.url, headers)).awaitSuccess().parseAs<BookDtoPartial>()
        }
    }

    internal suspend fun getAllBooksOfSeries(v1UrlBase: String, seriesId: String): List<BookDtoPartial> {
        with(json) {
            return client.newCall(GET("$v1UrlBase/series/$seriesId/books?unpaged=true", headers)).awaitSuccess().parseAs<SeriesBookListDtoPartial>().content ?: listOf()
        }
    }

    /**
     * Komga book progress starts from 1
     */
    internal suspend fun updateBookProgress(bookUrl: String, pageIndex: Int) {
        //TODO: rate limit
        val resp = client.newCall(
            Request.Builder()
                .url("${bookUrl}/read-progress")
                .patch("{\"page\": ${pageIndex + 1}}".toRequestBody("Application/json".toMediaType()))
                .build()
        ).awaitSuccess()
        logcat(LogPriority.DEBUG) { "update progress to ${pageIndex + 1} with $resp"  }
    }
}
