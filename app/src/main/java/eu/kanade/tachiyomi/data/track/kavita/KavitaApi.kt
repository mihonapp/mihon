package eu.kanade.tachiyomi.data.track.kavita

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException

class KavitaApi(private val client: OkHttpClient, interceptor: KavitaInterceptor) {
    private val authClient = client.newBuilder().dns(Dns.SYSTEM).addInterceptor(interceptor).build()
    fun getApiFromUrl(url: String): String {
        return url.split("/api/").first() + "/api"
    }

    fun getNewToken(apiUrl: String, apiKey: String): String? {
        /*
         * Uses url to compare against each source APIURL's to get the correct custom source preference.
         * Now having source preference we can do getString("APIKEY")
         * Authenticates to get the token
         * Saves the token in the var jwtToken
         */

        val request = POST(
            "$apiUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=Tachiyomi-Kavita",
            body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
        )
        try {
            client.newCall(request).execute().use {
                if (it.code == 200) {
                    return it.parseAs<AuthenticationDto>().token
                }
                if (it.code == 401) {
                    logcat(LogPriority.WARN) { "Unauthorized / api key not valid:Cleaned api URL:${apiUrl}Api key is empty:${apiKey.isEmpty()}" }
                    throw Exception("Unauthorized / api key not valid")
                }
                if (it.code == 500) {
                    logcat(LogPriority.WARN) { "Error fetching jwt token. Cleaned api URL:$apiUrl Api key is empty:${apiKey.isEmpty()}" }
                    throw Exception("Error fetching jwt token")
                }
            }
            // Not sure which one to cathc
        } catch (e: SocketTimeoutException) {
            logcat(LogPriority.WARN) {
                "Could not fetch jwt token. Probably due to connectivity issue or the url '$apiUrl' is not available. Skipping"
            }
            return null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Unhandled Exception fetching jwt token for url: '$apiUrl'"
            }
            throw e
        }

        return null
    }

    private fun getApiVolumesUrl(url: String): String {
        return "${getApiFromUrl(url)}/Series/volumes?seriesId=${getIdFromUrl(url)}"
    }

    private fun getIdFromUrl(url: String): Int {
        /*Strips serie id from Url*/
        return url.substringAfterLast("/").toInt()
    }

    private fun getTotalChapters(url: String): Int {
        /*Returns total chapters in the series.
         * Ignores volumes.
         * Volumes consisting of 1 file treated as chapter
         */
        val requestUrl = getApiVolumesUrl(url)
        try {
            val listVolumeDto = authClient.newCall(GET(requestUrl))
                .execute()
                .parseAs<List<VolumeDto>>()
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
        val serieId = getIdFromUrl(url)
        val requestUrl = "${getApiFromUrl(url)}/Tachiyomi/latest-chapter?seriesId=$serieId"
        try {
            authClient.newCall(GET(requestUrl))
                .execute().use {
                    if (it.code == 200) {
                        return it.parseAs<ChapterDto>().number!!.replace(",", ".").toFloat()
                    }
                    if (it.code == 204) {
                        return 0F
                    }
                }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Exception getting latest chapter read. Could not get itemRequest:$requestUrl" }
            throw e
        }
        return 0F
    }

    suspend fun getTrackSearch(url: String): TrackSearch =
        withIOContext {
            try {
                val serieDto: SeriesDto =
                    authClient.newCall(GET(url))
                        .await()
                        .parseAs<SeriesDto>()

                val track = serieDto.toTrack()

                track.apply {
                    cover_url = serieDto.thumbnail_url.toString()
                    tracking_url = url
                    total_chapters = getTotalChapters(url)

                    title = serieDto.name
                    status = when (serieDto.pagesRead) {
                        serieDto.pages -> Kavita.COMPLETED
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
        val requestUrl = "${getApiFromUrl(track.tracking_url)}/Tachiyomi/mark-chapter-until-as-read?seriesId=${getIdFromUrl(track.tracking_url)}&chapterNumber=${track.last_chapter_read}"
        authClient.newCall(POST(requestUrl, body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())))
            .await()
        return getTrackSearch(track.tracking_url)
    }
}
