package eu.kanade.tachiyomi.data.track.kavita

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import java.net.SocketTimeoutException

class KavitaApi(private val client: OkHttpClient, interceptor: KavitaInterceptor) {

    // Cache chapter tuples to avoid redundant API calls
    private val chapterTupleCache = mutableMapOf<String, List<Pair<ChapterType, ChapterDto>>>()

    private val json: Json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }

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
        } catch (_: SocketTimeoutException) {
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

    /* Strips series id from URL */
    private fun getIdFromUrl(url: String): Int {
        return url.substringAfterLast("/").toInt()
    }

    private suspend fun getChapterTuples(url: String): List<Pair<ChapterType, ChapterDto>> {
        // Return cached result if available
        chapterTupleCache[url]?.let { cached ->
            logcat(LogPriority.VERBOSE) { "Using cached chapter tuples for $url" }
            return cached
        }

        val requestUrl = getApiVolumesUrl(url)
        return try {
            val listVolumeDto = with(json) {
                authClient.newCall(GET(requestUrl))
                    .awaitSuccess()
                    .parseAs<List<VolumeDto>>()
            }

            val tuples = listVolumeDto.flatMap { volume ->
                volume.chapters.map { chapter ->
                    ChapterType.of(chapter, volume) to chapter
                }
            }

            // Cache the result
            chapterTupleCache[url] = tuples

            logcat(LogPriority.DEBUG) { "Cached ${tuples.size} chapter tuples for $url" }
            tuples
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Exception fetching chapters" }
            emptyList()
        }
    }

    // Cache invalidation method
    private fun invalidateCache(url: String? = null) {
        if (url != null) {
            chapterTupleCache.remove(url)
            logcat(LogPriority.DEBUG) { "Invalidated cache for $url" }
        } else {
            chapterTupleCache.clear()
            logcat(LogPriority.DEBUG) { "Invalidated all caches" }
        }
    }

    /**
     * Returns total chapters in the series for Mihon's UI display.
     *
     * In mixed content (both chapters AND volumes/specials),
     * ONLY count regular chapters. This prevents Mihon from showing "3/66" when
     * there are 3 chapters and 63 volume/special files.
     */
    private suspend fun getTotalChapters(url: String): Long {
        val chapterTuples = getChapterTuples(url)

        // Early exit for empty libraries
        if (chapterTuples.isEmpty()) {
            logcat(LogPriority.DEBUG) { "No chapters found, returning 0 total" }
            return 0L
        }

        // Determine content composition
        val regularChapters = chapterTuples.filter { (type, _) ->
            type in listOf(ChapterType.Regular, ChapterType.Chapter, ChapterType.Issue)
        }
        val nonRegularItems = chapterTuples.filter { (type, _) ->
            type in listOf(ChapterType.SingleFileVolume, ChapterType.Special)
        }

        // Debug logging
        logcat(LogPriority.DEBUG) {
            "Content analysis: regular=${regularChapters.size}, " +
                "volumes/specials=${nonRegularItems.size}, " +
                "total=${chapterTuples.size}"
        }

        // Mixed content: Return ONLY regular chapter count
        if (regularChapters.isNotEmpty() && nonRegularItems.isNotEmpty()) {
            val maxRegularChapter = regularChapters.maxOfOrNull { (_, ch) -> ch.minNumber.toLong() } ?: 0L
            logcat(LogPriority.DEBUG) {
                "Mixed library: returning max regular chapter = $maxRegularChapter"
            }
            return maxRegularChapter
        }

        // Only volumes/specials: Count items
        if (nonRegularItems.isNotEmpty()) {
            logcat(LogPriority.DEBUG) {
                "Volumes-only library: returning item count = ${nonRegularItems.size}"
            }
            return nonRegularItems.size.toLong()
        }

        // Only chapters: Return max chapter number
        val maxChapter = regularChapters.maxOfOrNull { (_, ch) -> ch.minNumber.toLong() } ?: 0L
        logcat(LogPriority.DEBUG) { "Chapters-only library: returning max = $maxChapter" }
        return maxChapter
    }

    /**
     * Fetches the latest chapter read from Kavita's API.
     * For mixed content, filters out volumes/specials and returns only the latest regular chapter.
     */
    private suspend fun getLatestChapterRead(url: String): Double {
        val seriesId = getIdFromUrl(url)
        val apiUrl = getApiFromUrl(url)

        return try {
            val response = authClient.newCall(GET("$apiUrl/Tachiyomi/latest-chapter?seriesId=$seriesId"))
                .awaitSuccess()

            // Empty response means no progress in Kavita
            if (response.body.contentLength() == 0L) {
                logcat(LogPriority.DEBUG) { "API returned empty response - no progress in Kavita" }
                return 0.0
            }

            val chapterDto = with(json) {
                response.parseAs<ChapterDto>()
            }

            logcat(LogPriority.VERBOSE) {
                "API latest chapter: #${chapterDto.minNumber} (vol=${chapterDto.volumeId}, special=${chapterDto.isSpecial})"
            }

            // TRUST THE API: Return the chapter number as-is
            // The API should return synthetic numbers (0.0001) for volumes/specials in mixed-content
            chapterDto.minNumber
        } catch (e: SerializationException) {
            logcat(LogPriority.WARN, e) { "API parse failed, falling back to local calculation" }
            getLatestChapterReadFromTuples(url)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "API call failed, falling back to local calculation" }
            getLatestChapterReadFromTuples(url)
        }
    }

    /**
     * Fallback method to calculate the latest chapter read from local chapter data.
     * Matches the extension's chapter number calculation logic.
     */
    private suspend fun getLatestChapterReadFromTuples(url: String): Double {
        val chapterTuples = getChapterTuples(url)
        val hasRegularChapters = chapterTuples.any { (type, _) ->
            type in listOf(ChapterType.Regular, ChapterType.Chapter, ChapterType.Issue)
        }

        // For fallback, we need to find the highest chapter number as the extension would calculate it
        return if (hasRegularChapters) {
            // In mixed content, return the highest regular chapter number as fallback
            logcat(LogPriority.WARN) { "Using fallback for mixed content - returning highest regular chapter number" }
            chapterTuples
                .filter { (type, _) -> type in listOf(ChapterType.Regular, ChapterType.Chapter, ChapterType.Issue) }
                .maxByOrNull { (_, ch) -> ch.minNumber }
                ?.second?.minNumber ?: 0.0
        } else {
            // Volumes-only: return highest item number
            logcat(LogPriority.WARN) { "Using fallback for volumes-only content - returning highest item number" }
            chapterTuples.maxByOrNull { (_, ch) -> ch.minNumber }
                ?.second?.minNumber ?: 0.0
        }
    }

    suspend fun getTrackSearch(url: String): TrackSearch = withIOContext {
        try {
            getSeriesTrack(url)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not get item: $url" }
            throw e
        }
    }

    private suspend fun getSeriesTrack(url: String): TrackSearch {
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
        return track
    }

    suspend fun updateProgress(track: Track, didReadChapter: Boolean = false): Track {
        try {
            logcat(LogPriority.DEBUG) {
                "updateProgress called: last_chapter_read=${track.last_chapter_read}, didReadChapter=$didReadChapter"
            }
            updateSeriesProgress(track)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to mark as read in Kavita" }
        }
        return track
    }

    private suspend fun updateSeriesProgress(track: Track) {
        val seriesId = getIdFromUrl(track.tracking_url)
        val apiUrl = getApiFromUrl(track.tracking_url)

        // Determine content type and use appropriate endpoint
        val hasRegularChapters = hasRegularChapters(track.tracking_url)

        if (hasRegularChapters) {
            // Mixed-content: Use bulk endpoint for everything
            logcat(LogPriority.DEBUG) {
                "Mixed-content: marking chapters until ${track.last_chapter_read} as read via bulk endpoint"
            }

            authClient.newCall(
                POST(
                    "$apiUrl/Tachiyomi/mark-chapter-until-as-read?seriesId=$seriesId&chapterNumber=${track.last_chapter_read}",
                    body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                )
            ).awaitSuccess()
        } else {
            // Volumes/Specials-only: Mark each volume individually
            updateVolumesOnlyProgress(seriesId, apiUrl, track.last_chapter_read)
        }

        // Invalidate cache after updating
        invalidateCache(track.tracking_url)
    }

    private suspend fun hasRegularChapters(url: String): Boolean {
        val chapterTuples = getChapterTuples(url)
        return chapterTuples.any { (type, _) ->
            type in listOf(ChapterType.Regular, ChapterType.Chapter, ChapterType.Issue)
        }
    }

    private suspend fun updateVolumesOnlyProgress(
        seriesId: Int,
        apiUrl: String,
        lastChapterRead: Double
    ) {
        val requestUrl = "$apiUrl/Series/volumes?seriesId=$seriesId"
        val volumes = try {
            with(json) {
                authClient.newCall(GET(requestUrl))
                    .awaitSuccess()
                    .parseAs<List<VolumeDto>>()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to fetch volumes for marking read" }
            return
        }

        val chaptersToMark = volumes.flatMap { volume -> volume.chapters }
            .sortedBy { it.minNumber }
            .take(lastChapterRead.toInt())

        logcat(LogPriority.DEBUG) {
            "Volumes-only: marking ${chaptersToMark.size} items as read"
        }

        chaptersToMark.forEach { chapter ->
            val payload = """{"seriesId":$seriesId,"volumeId":${chapter.volumeId}}"""
            authClient.newCall(
                POST(
                    "$apiUrl/Reader/mark-volume-read",
                    body = payload.toRequestBody("application/json".toMediaTypeOrNull())
                )
            ).awaitSuccess()
        }
    }


}
