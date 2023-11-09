package eu.kanade.tachiyomi.data.track.kavita

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.SocketTimeoutException

class KavitaApi(private val client: OkHttpClient, interceptor: KavitaInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder()
        .dns(Dns.SYSTEM)
        .addInterceptor(interceptor)
        .build()

    fun getApiFromUrl(url: String): String {
        return url.split("/api/").first() + "/api"
    }

    /*
     * Uses url to compare against each source APIURL's to get the correct custom source preference.
     * Now having source preference we can do getString("APIKEY")
     * Authenticates to get the token
     * Saves the token in the var jwtToken
     */
    fun getNewToken(apiUrl: String, apiKey: String): String? {
        val request = POST(
            "$apiUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=Tachiyomi-Kavita",
            body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
        )
        try {
            with(json) {
                client.newCall(request).execute().use {
                    when (it.code) {
                        200 -> return it.parseAs<AuthenticationDto>().token
                        401 -> {
                            logcat(LogPriority.WARN) {
                                "Unauthorized / API key not valid: API URL: $apiUrl, empty API key: ${apiKey.isEmpty()}"
                            }
                            throw IOException("Unauthorized / api key not valid")
                        }
                        500 -> {
                            logcat(
                                LogPriority.WARN,
                            ) { "Error fetching JWT token. API URL: $apiUrl, empty API key: ${apiKey.isEmpty()}" }
                            throw IOException("Error fetching JWT token")
                        }
                        else -> {}
                    }
                }
            }
            // Not sure which one to catch
        } catch (e: SocketTimeoutException) {
            logcat(LogPriority.WARN) {
                "Could not fetch JWT token. Probably due to connectivity issue or URL '$apiUrl' not available, skipping"
            }
            return null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Unhandled exception fetching JWT token for URL: '$apiUrl'"
            }
            throw IOException(e)
        }

        return null
    }

    private fun getApiVolumesUrl(url: String): String {
        return "${getApiFromUrl(url)}/Series/volumes?seriesId=${getIdFromUrl(url)}"
    }

    /* Strips serie id from URL */
    private fun getIdFromUrl(url: String): Int {
        return url.substringAfterLast("/").toInt()
    }

    /*
     * Returns total chapters in the series.
     * Ignores volumes.
     * Volumes consisting of 1 file treated as chapter
     */
    private fun getTotalChapters(url: String): Int {
        val requestUrl = getApiVolumesUrl(url)
        try {
            val listVolumeDto = with(json) {
                authClient.newCall(GET(requestUrl))
                    .execute()
                    .parseAs<List<VolumeDto>>()
            }
            var volumeNumber = 0
            var maxChapterNumber = 0
            for (volume in listVolumeDto) {
                if (volume.chapters.maxOf { it.number!!.toFloat() } == 0f) {
                    volumeNumber++
                } else if (maxChapterNumber < volume.chapters.maxOf { it.number!!.toFloat() }) {
                    maxChapterNumber = volume.chapters.maxOf { it.number!!.toFloat().toInt() }
                }
            }

            return if (maxChapterNumber > volumeNumber) maxChapterNumber else volumeNumber
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Exception fetching Total Chapters. Request:$requestUrl" }
            throw e
        }
    }

    private fun getLatestChapterRead(url: String): Float {
        val seriesId = getIdFromUrl(url)
        val requestUrl = "${getApiFromUrl(url)}/Tachiyomi/latest-chapter?seriesId=$seriesId"
        try {
            with(json) {
                authClient.newCall(GET(requestUrl)).execute().use {
                    if (it.code == 200) {
                        return it.parseAs<ChapterDto>().number!!.replace(",", ".").toFloat()
                    }
                    if (it.code == 204) {
                        return 0F
                    }
                }
            }
        } catch (e: Exception) {
            logcat(
                LogPriority.WARN,
                e,
            ) { "Exception getting latest chapter read. Could not get itemRequest: $requestUrl" }
            throw e
        }
        return 0F
    }

    suspend fun getTrackSearch(url: String): TrackSearch = withIOContext {
        try {
            val seriesDto: SeriesDto = with(json) {
                authClient.newCall(GET(url))
                    .awaitSuccess()
                    .parseAs()
            }

            val track = seriesDto.toTrack()
            track.apply {
                cover_url = seriesDto.thumbnail_url.toString()
                tracking_url = url
                total_chapters = getTotalChapters(url)

                title = seriesDto.name
                status = when (seriesDto.pagesRead) {
                    seriesDto.pages -> Kavita.COMPLETED
                    0 -> Kavita.UNREAD
                    else -> Kavita.READING
                }
                last_chapter_read = getLatestChapterRead(url)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not get item: $url" }
            throw e
        }
    }

    suspend fun updateProgress(track: Track): Track {
        val requestUrl = "${getApiFromUrl(
            track.tracking_url,
        )}/Tachiyomi/mark-chapter-until-as-read?seriesId=${getIdFromUrl(
            track.tracking_url,
        )}&chapterNumber=${track.last_chapter_read}"
        authClient.newCall(
            POST(requestUrl, body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())),
        )
            .awaitSuccess()
        return getTrackSearch(track.tracking_url)
    }
}
