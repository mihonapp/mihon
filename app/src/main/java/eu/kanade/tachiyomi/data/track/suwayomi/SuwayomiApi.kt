package eu.kanade.tachiyomi.data.track.suwayomi

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.nio.charset.Charset
import java.security.MessageDigest

class SuwayomiApi(private val trackId: Long) {

    private val json: Json by injectLazy()

    private val sourceManager: SourceManager by injectLazy()
    private val source: HttpSource by lazy { (sourceManager.get(sourceId) as HttpSource) }
    private val client: OkHttpClient by lazy { source.client }
    private val headers: Headers by lazy { source.headers }
    private val baseUrl: String by lazy { source.baseUrl.trimEnd('/') }

    suspend fun getTrackSearch(trackUrl: String): TrackSearch = withIOContext {
        val url = try {
            // test if getting api url or manga id
            val mangaId = trackUrl.toLong()
            "$baseUrl/api/v1/manga/$mangaId"
        } catch (e: NumberFormatException) {
            trackUrl
        }

        val manga = with(json) {
            client.newCall(GET("$url/full", headers))
                .awaitSuccess()
                .parseAs<MangaDataClass>()
        }

        TrackSearch.create(trackId).apply {
            title = manga.title
            cover_url = "$url/thumbnail"
            summary = manga.description.orEmpty()
            tracking_url = url
            total_chapters = manga.chapterCount
            publishing_status = manga.status
            last_chapter_read = manga.lastChapterRead?.chapterNumber ?: 0.0
            status = when (manga.unreadCount) {
                manga.chapterCount -> Suwayomi.UNREAD
                0L -> Suwayomi.COMPLETED
                else -> Suwayomi.READING
            }
        }
    }

    suspend fun updateProgress(track: Track): Track {
        val url = track.tracking_url
        val chapters = with(json) {
            client.newCall(GET("$url/chapters", headers))
                .awaitSuccess()
                .parseAs<List<ChapterDataClass>>()
        }
        val lastChapterIndex = chapters.first { it.chapterNumber == track.last_chapter_read }.index

        client.newCall(
            PUT(
                "$url/chapter/$lastChapterIndex",
                headers,
                FormBody.Builder(Charset.forName("utf8"))
                    .add("markPrevRead", "true")
                    .add("read", "true")
                    .build(),
            ),
        ).awaitSuccess()

        return getTrackSearch(track.tracking_url)
    }

    private val sourceId by lazy {
        val key = "tachidesk/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}
