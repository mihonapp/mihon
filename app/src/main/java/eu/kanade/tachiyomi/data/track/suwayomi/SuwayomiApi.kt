package eu.kanade.tachiyomi.data.track.suwayomi

import android.content.SharedPreferences
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.sourcePreferences
import logcat.LogPriority
import mihon.graphql.suwayomi.SuwayomiGetMangaQuery
import mihon.graphql.suwayomi.SuwayomiGetMangaUnreadChaptersQuery
import mihon.graphql.suwayomi.SuwayomiMarkAndDeleteChaptersMutation
import mihon.graphql.suwayomi.SuwayomiMarkChaptersReadMutation
import mihon.graphql.suwayomi.SuwayomiUpdateMangaProgressMutation
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
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
            .build()
    }

    fun sourcePreferences(): SharedPreferences = configurableSource.sourcePreferences()

    suspend fun getTrackSearch(mangaId: Long): TrackSearch? {
        val response = graphQlClient
            .query(
                SuwayomiGetMangaQuery(mangaId = mangaId.toInt()),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.manga.mangaFragment.toTrackSearch(trackId, baseUrl)
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "Suwayomi Details error: ${it.message}" } }
            }
            null
        }
    }

    suspend fun updateProgress(track: Track, deleteDownloadsOnServer: Boolean = false): Track? {
        val mangaId = track.remote_id

        val chaptersResponse = graphQlClient
            .query(
                SuwayomiGetMangaUnreadChaptersQuery(
                    mangaId = mangaId.toInt(),
                    chapterNumber = track.last_chapter_read,
                ),
            )
            .awaitSuccess()

        val data = chaptersResponse.data
        val chaptersToMark = data
            ?.chapters
            ?.nodes
            ?.map { it.id }
            ?: throw Exception("Could not get chapters data")

        if (chaptersToMark.isEmpty()) {
            return getTrackSearch(mangaId)
        }

        val markMutation = if (deleteDownloadsOnServer) {
            SuwayomiMarkAndDeleteChaptersMutation(chapters = chaptersToMark)
        } else {
            SuwayomiMarkChaptersReadMutation(chapters = chaptersToMark)
        }

        val markingResponse = graphQlClient
            .mutation(markMutation)
            .awaitSuccess()

        if (markingResponse.hasErrors()) {
            markingResponse.errors?.forEach { logcat(LogPriority.ERROR) { "Suwayomi Mark error: ${it.message}" } }
        }

        val updateResponse = graphQlClient
            .mutation(
                SuwayomiUpdateMangaProgressMutation(mangaId = mangaId.toInt()),
            )
            .awaitSuccess()

        if (updateResponse.hasErrors()) {
            updateResponse.errors?.forEach {
                logcat(LogPriority.ERROR) { "Suwayomi Track Progress error: ${it.message}" }
            }
        }

        return getTrackSearch(mangaId)
    }

    private val sourceId by lazy {
        val key = "tachidesk/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}
