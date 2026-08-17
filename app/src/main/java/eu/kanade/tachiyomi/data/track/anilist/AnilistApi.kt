package eu.kanade.tachiyomi.data.track.anilist

import android.net.Uri
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUser
import eu.kanade.tachiyomi.data.track.anilist.dto.toALUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import mihon.graphql.anilist.AniListAddMangaMutation
import mihon.graphql.anilist.AniListDeleteMangaMutation
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.AniListGetLibMangaQuery
import mihon.graphql.anilist.AniListGetMangaDetailsQuery
import mihon.graphql.anilist.AniListSearchMangaQuery
import mihon.graphql.anilist.AniListUpdateMangaMutation
import mihon.graphql.anilist.type.FuzzyDateInput
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import tachiyomi.domain.track.model.Track as DomainTrack

class AnilistApi(
    val trackId: Long,
    val client: OkHttpClient,
    interceptor: AnilistInterceptor,
) {

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(permits = 85, period = 1.minutes)
        .build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            .build()
    }

    suspend fun addLibManga(track: Track): Track {
        val response = graphQlClient
            .mutation(
                AniListAddMangaMutation(
                    manga_id = track.remote_id.toInt(),
                    progress = track.last_chapter_read.toInt(),
                    status = track.toApiStatus2(),
                    private = track.private,
                ),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.SaveMediaListEntry?.id?.let {
                track.library_id = it.toLong()
                track
            }
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Add error: ${it.message}" } }
            }
            null
        }
            ?: throw Exception("Failed to add manga")
    }

    suspend fun updateLibManga(track: Track): Track {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "AniList cannot update track with null library_id" }

        val response = graphQlClient
            .mutation(
                AniListUpdateMangaMutation(
                    library_id = libraryId.toInt(),
                    progress = track.last_chapter_read.toInt(),
                    status = track.toApiStatus2(),
                    private = track.private,
                    score = track.score.toInt(),
                    startedAt = createFuzzyDate(track.started_reading_date),
                    completedAt = createFuzzyDate(track.finished_reading_date),
                ),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            track
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Update error: ${it.message}" } }
            }
            null
        }
            ?: throw Exception("Failed to update manga")
    }

    suspend fun deleteLibManga2(track: DomainTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "AniList cannot update track with null library_id" }

        val response = graphQlClient
            .mutation(
                AniListDeleteMangaMutation(library_id = libraryId.toInt()),
            )
            .awaitSuccess()

        if (response.hasErrors()) {
            response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Delete error: ${it.message}" } }
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        val response = graphQlClient
            .query(
                AniListSearchMangaQuery(search = search),
            )
            .awaitSuccess()

        // extracted for smart casting
        val data = response.data
        return if (data != null) {
            data.Page?.media?.mapNotNull { it?.toTrackSearch(trackId) } ?: emptyList()
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Search error: ${it.message}" } }
            }
            emptyList()
        }
    }

    suspend fun findLibManga(track: Track, userId: Int): Track? {
        val response = graphQlClient
            .query(
                AniListGetLibMangaQuery(
                    user_id = userId,
                    manga_id = track.remote_id.toInt(),
                ),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.Page?.mediaList?.firstOrNull()?.toTrack(trackId)
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Find error: ${it.message}" } }
            }
            null
        }
    }

    fun createOAuth(token: String): ALOAuth {
        return ALOAuth(token, "Bearer", System.currentTimeMillis() + 31536000000, 31536000000)
    }

    suspend fun getCurrentUser(): ALUser {
        val response = graphQlClient
            .query(AniListGetCurrentUserQuery())
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.Viewer?.toALUser()
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Get User error: ${it.message}" } }
            }
            null
        }
            ?: throw Exception("Failed to get AniList user data")
    }

    suspend fun getMangaDetails(id: Int): TrackSearch? {
        val response = graphQlClient
            .query(
                AniListGetMangaDetailsQuery(manga_id = id),
            )
            .awaitSuccess()

        val data = response.data
        return if (data != null) {
            data.Page?.media?.firstOrNull()?.toTrackSearch(trackId)
        } else {
            if (response.hasErrors()) {
                response.errors?.forEach { logcat(LogPriority.ERROR) { "AniList Get Details error: ${it.message}" } }
            }
            null
        }
    }

    private fun createFuzzyDate(dateValue: Long): FuzzyDateInput {
        // all absent/null
        if (dateValue == 0L) return FuzzyDateInput()

        val dateTime = Instant.fromEpochMilliseconds(dateValue).toLocalDateTime(TimeZone.currentSystemDefault())
        return FuzzyDateInput(
            year = Optional.present(dateTime.year),
            month = Optional.present(dateTime.month.number),
            day = Optional.present(dateTime.day),
        )
    }

    companion object {
        private const val CLIENT_ID = "16329"

        fun authUrl(): Uri = "https://anilist.co/api/v2/oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
    }
}
