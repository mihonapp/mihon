package eu.kanade.tachiyomi.data.track.suwayomi

import android.content.SharedPreferences
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.coroutines.Dispatchers
import mihon.graphql.suwayomi.SuwayomiGetMangaQuery
import mihon.graphql.suwayomi.SuwayomiGetMangaUnreadChaptersQuery
import mihon.graphql.suwayomi.SuwayomiMarkAndDeleteChaptersMutation
import mihon.graphql.suwayomi.SuwayomiMarkChaptersReadMutation
import mihon.graphql.suwayomi.SuwayomiUpdateMangaProgressMutation
import okhttp3.OkHttpClient
import tachiyomi.domain.source.service.SourceManager
import java.security.MessageDigest

class SuwayomiApi(
    private val trackId: Long,
    private val sourceManager: SourceManager,
) {
    private val source: Source by lazy { sourceManager.get(sourceId)!! }
    private val httpSource: HttpSource by lazy { source as HttpSource }
    private val configurableSource: ConfigurableSource by lazy { source as ConfigurableSource }
    private val client: OkHttpClient by lazy { httpSource.client }
    private val baseUrl: String by lazy { httpSource.baseUrl.trimEnd('/') }
    private val apiUrl: String by lazy { "$baseUrl/api/graphql" }

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl(apiUrl)
            .okHttpClient(client)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    fun sourcePreferences(): SharedPreferences = configurableSource.sourcePreferences()

    suspend fun getTrackSearch(mangaId: Long): TrackSearch? {
        return graphQlClient
            .query(
                SuwayomiGetMangaQuery(mangaId = mangaId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "Suwayomi: Failed to find manga in library",
                default = { null },
            ) {
                it.manga.mangaFragment.toTrackSearch(trackId, baseUrl)
            }
    }

    suspend fun updateProgress(track: Track, deleteDownloadsOnServer: Boolean = false): Track? {
        val mangaId = track.remote_id

        val chaptersToMark = graphQlClient
            .query(
                SuwayomiGetMangaUnreadChaptersQuery(
                    mangaId = mangaId.toInt(),
                    chapterNumber = track.last_chapter_read,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Suwayomi: Failed to get chapters data",
                default = { null },
            ) {
                it.chapters.nodes.map { chapter -> chapter.id }
            }
            ?: throw Exception("Could not get chapters data")

        if (chaptersToMark.isEmpty()) {
            return getTrackSearch(mangaId)
        }

        val markMutation = if (deleteDownloadsOnServer) {
            SuwayomiMarkAndDeleteChaptersMutation(chapters = chaptersToMark)
        } else {
            SuwayomiMarkChaptersReadMutation(chapters = chaptersToMark)
        }

        graphQlClient
            .mutation(markMutation)
            .execute()
            .dataOrElse(
                errorLog = "Suwayomi: Failed to mark chapters",
                default = {},
            ) {}

        graphQlClient
            .mutation(
                SuwayomiUpdateMangaProgressMutation(mangaId = mangaId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "Suwayomi: Failed to update progress",
                default = {},
            ) {}

        return getTrackSearch(mangaId)
    }

    private val sourceId by lazy {
        val key = "tachidesk/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}
