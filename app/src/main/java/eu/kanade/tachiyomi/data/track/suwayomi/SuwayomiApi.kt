package eu.kanade.tachiyomi.data.track.suwayomi

import android.content.SharedPreferences
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.coroutines.Dispatchers
import mihon.graphql.suwayomi.SuwayomiGetMangaQuery
import mihon.graphql.suwayomi.SuwayomiGetMangaUnreadChaptersQuery
import mihon.graphql.suwayomi.SuwayomiMarkAndDeleteChaptersMutation
import mihon.graphql.suwayomi.SuwayomiMarkChaptersReadMutation
import mihon.graphql.suwayomi.SuwayomiUpdateMangaProgressMutation
import tachiyomi.domain.source.service.SourceManager
import java.security.MessageDigest

class SuwayomiApi(
    private val trackerId: Long,
    private val sourceManager: SourceManager,
) {
    @Volatile
    private var connection: Connection? = null

    private suspend fun connection(): Connection {
        connection?.let { return it }
        val source = sourceManager.get(sourceId) as HttpSource
        val baseUrl = source.baseUrl.trimEnd('/')
        val graphQlClient = ApolloClient.Builder()
            .serverUrl("$baseUrl/api/graphql")
            .okHttpClient(source.client)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
        return Connection(source, baseUrl, graphQlClient).also { connection = it }
    }

    suspend fun sourcePreferences(): SharedPreferences {
        return (connection().source as ConfigurableSource).sourcePreferences()
    }

    suspend fun getTrackSearch(mangaId: Long): TrackSearch? {
        val connection = connection()
        return connection.graphQlClient
            .query(
                SuwayomiGetMangaQuery(mangaId = mangaId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "Suwayomi: Failed to find manga in library",
                default = { null },
            ) {
                it.manga.mangaFragment.toTrackSearch(trackerId, connection.baseUrl)
            }
    }

    suspend fun updateProgress(track: Track, deleteDownloadsOnServer: Boolean = false): Track? {
        val graphQlClient = connection().graphQlClient
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

    private class Connection(
        val source: HttpSource,
        val baseUrl: String,
        val graphQlClient: ApolloClient,
    )
}
