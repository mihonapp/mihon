package eu.kanade.tachiyomi.data.track.suwayomi

import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

class SuwayomiApi(private val trackId: Long) {

    private val json: Json by injectLazy()

    private val sourceManager: SourceManager by injectLazy()
    private val source: HttpSource by lazy { (sourceManager.get(sourceId) as HttpSource) }
    private val configurableSource: ConfigurableSource by lazy { (sourceManager.get(sourceId) as ConfigurableSource) }
    private val client: OkHttpClient by lazy { source.client }
    private val baseUrl: String by lazy { source.baseUrl.trimEnd('/') }
    private val apiUrl: String by lazy { "$baseUrl/api/graphql" }

    public fun sourcePreferences(): SharedPreferences = configurableSource.sourcePreferences()

    suspend fun getTrackSearch(mangaId: Long): TrackSearch = withIOContext {
        val query = $$"""
        |query GetManga($mangaId: Int!) {
        |    manga(id: $mangaId) {
        |        ...MangaFragment
        |    }
        |}
        |
        |$$MangaFragment
        """.trimMargin()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("mangaId", mangaId)
            }
        }
        val manga = with(json) {
            client.newCall(
                POST(
                    apiUrl,
                    body = payload.toString().toRequestBody(jsonMime),
                ),
            )
                .awaitSuccess()
                .parseAs<GetMangaResult>()
                .data
                .entry
        }

        TrackSearch.create(trackId).apply {
            remote_id = mangaId
            title = manga.title
            cover_url = "$baseUrl/${manga.thumbnailUrl}"
            summary = manga.description.orEmpty()
            tracking_url = "$baseUrl/manga/$mangaId"
            total_chapters = manga.chapters.totalCount.toLong()
            publishing_status = manga.status.name
            last_chapter_read = manga.latestReadChapter?.chapterNumber ?: 0.0
            status = when (manga.unreadCount) {
                manga.chapters.totalCount -> Suwayomi.UNREAD
                0 -> Suwayomi.COMPLETED
                else -> Suwayomi.READING
            }
        }
    }

    suspend fun updateProgress(track: Track, deleteDownloadsOnServer: Boolean = false): Track {
        val mangaId = track.remote_id

        // TODO: Include a filter on the chapter number here
        // Below, we only consider older chapters; since v2.1.1985 filtering works properly in the query
        val chaptersQuery = $$"""
        |query GetMangaUnreadChapters($mangaId: Int!) {
        |  chapters(condition: {mangaId: $mangaId, isRead: false}) {
        |    nodes {
        |      id
        |      chapterNumber
        |    }
        |  }
        |}
        """.trimMargin()
        val chaptersPayload = buildJsonObject {
            put("query", chaptersQuery)
            putJsonObject("variables") {
                put("mangaId", mangaId)
            }
        }
        val chaptersToMark = with(json) {
            client.newCall(
                POST(
                    apiUrl,
                    body = chaptersPayload.toString().toRequestBody(jsonMime),
                ),
            )
                .awaitSuccess()
                .parseAs<GetMangaUnreadChaptersResult>()
                .data
                .entry
                .nodes
                .mapNotNull { n -> n.id.takeIf { n.chapterNumber <= track.last_chapter_read + 0.001 } }
        }

        val markQuery = if (deleteDownloadsOnServer) {
            $$"""
            |mutation MarkChaptersRead($chapters: [Int!]!) {
            |  updateChapters(input: {ids: $chapters, patch: {isRead: true}}) {
            |    __typename
            |  }
            |  deleteDownloadedChapters(input: {ids: $chapters}) {
            |    __typename
            |  }
            |}
            """.trimMargin()
        } else {
            $$"""
            |mutation MarkChaptersRead($chapters: [Int!]!) {
            |  updateChapters(input: {ids: $chapters, patch: {isRead: true}}) {
            |    __typename
            |  }
            |}
            """.trimMargin()
        }
        val markPayload = buildJsonObject {
            put("query", markQuery)
            putJsonObject("variables") {
                putJsonArray("chapters") {
                    addAll(chaptersToMark)
                }
            }
        }
        with(json) {
            client.newCall(
                POST(
                    apiUrl,
                    body = markPayload.toString().toRequestBody(jsonMime),
                ),
            )
                .awaitSuccess()
        }

        val trackQuery = $$"""
        |mutation TrackManga($mangaId: Int!) {
        |  trackProgress(input: {mangaId: $mangaId}) {
        |    __typename
        |  }
        |}
        """.trimMargin()
        val trackPayload = buildJsonObject {
            put("query", trackQuery)
            putJsonObject("variables") {
                put("mangaId", mangaId)
            }
        }
        with(json) {
            client.newCall(
                POST(
                    apiUrl,
                    body = trackPayload.toString().toRequestBody(jsonMime),
                ),
            )
                .awaitSuccess()
        }

        return getTrackSearch(track.remote_id)
    }

    private val sourceId by lazy {
        val key = "tachidesk/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    companion object {
        private val MangaFragment = """
        |fragment MangaFragment on MangaType {
        |    artist
        |    author
        |    description
        |    id
        |    status
        |    thumbnailUrl
        |    title
        |    url
        |    genre
        |    inLibraryAt
        |    chapters {
        |        totalCount
        |    }
        |    latestUploadedChapter {
        |        uploadDate
        |    }
        |    latestFetchedChapter {
        |        fetchedAt
        |    }
        |    latestReadChapter {
        |        lastReadAt
        |        chapterNumber
        |    }
        |    unreadCount
        |    downloadCount
        |}
        """.trimMargin()
    }
}
