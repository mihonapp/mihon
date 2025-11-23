package eu.kanade.tachiyomi.data.track.anilist

import android.graphics.Color
import androidx.core.net.toUri
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihonx.auth.Auth
import mihonx.auth.models.AuthSession
import mihonx.auth.models.User
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.domain.track.model.Track as DomainTrack

class Anilist(id: Long) : BaseTracker(id, "AniList"), DeletableTracker, Auth.OAuth {

    companion object {
        private const val CLIENT_ID = "16329"
        private const val BASE_URL = "https://anilist.co/api/v2"

        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REREADING = 6L

        const val POINT_100 = "POINT_100"
        const val POINT_10 = "POINT_10"
        const val POINT_10_DECIMAL = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"

        private const val SCORE_FORMAT = "score_format"
        private const val ACCESS_TOKEN = "access_token"
    }

    private val authSessionPreference = trackPreferences.authSession(this)

    private val scoreFormat: String
        get() = authSessionPreference.get()?.memo[SCORE_FORMAT] ?: POINT_10

    private val userId: Int?
        get() = authSessionPreference.get()?.user?.id?.toIntOrNull()

    private val interceptor = AnilistInterceptor(
        token = authSessionPreference.get()?.memo[ACCESS_TOKEN]?.let(AnilistToken::from),
        onTokenExpired = {
            authSessionPreference.getAndSet { it?.copy(authExpired = true) }
        },
        onAuthRevoked = {
            authSessionPreference.getAndSet { it?.copy(authRevoked = true) }
        },
    )

    private val api by lazy { AnilistApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    override val authSession: Flow<AuthSession?>
        get() = authSessionPreference.changes()

    override val isLoggedIn: Boolean
        get() = authSessionPreference.get() != null

    override val isLoggedInFlow: Flow<Boolean>
        get() = authSession.map { it != null }

    override fun getOAuthUrl(state: String): String {
        return "$BASE_URL/oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    override suspend fun onOAuthCallback(data: Map<String, String>): Boolean {
        val accessToken = data[ACCESS_TOKEN] ?: return false
        val alToken = AnilistToken.from(accessToken)
        interceptor.updateToken(alToken)
        val alUser = api.getCurrentUser()
        val user = User(
            id = alToken.decoded.id,
            name = alUser.name,
            subtitle = if (alUser.donatorTier > 0) {
                alUser.donatorBadge
            } else {
                null
            },
            avatar = alUser.avatar.large,
        )
        val session = AuthSession(
            user = user,
            authExpired = false,
            authRevoked = false,
            memo = mapOf(
                ACCESS_TOKEN to accessToken,
                SCORE_FORMAT to alUser.mediaListOptions.scoreFormat,
            ),
        )

        trackPreferences.authSession(this).set(session)
        return true
    }

    override fun getLogo() = R.drawable.ic_tracker_anilist

    override fun getLogoColor() = Color.rgb(18, 25, 35)

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = REREADING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> {
        return when (scoreFormat) {
            // 10 point
            POINT_10 -> IntRange(0, 10).map(Int::toString).toImmutableList()
            // 100 point
            POINT_100 -> IntRange(0, 100).map(Int::toString).toImmutableList()
            // 5 stars
            POINT_5 -> IntRange(0, 5).map { "$it ★" }.toImmutableList()
            // Smiley
            POINT_3 -> persistentListOf("-", "😦", "😐", "😊")
            // 10 point decimal
            POINT_10_DECIMAL -> IntRange(0, 100).map { (it / 10f).toString() }.toImmutableList()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun get10PointScore(track: DomainTrack): Double {
        // Score is stored in 100 point format
        return track.score / 10.0
    }

    override fun indexToScore(index: Int): Double {
        return when (scoreFormat) {
            // 10 point
            POINT_10 -> index * 10.0
            // 100 point
            POINT_100 -> index.toDouble()
            // 5 stars
            POINT_5 -> when (index) {
                0 -> 0.0
                else -> index * 20.0 - 10.0
            }
            // Smiley
            POINT_3 -> when (index) {
                0 -> 0.0
                else -> index * 25.0 + 10.0
            }
            // 10 point decimal
            POINT_10_DECIMAL -> index.toDouble()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun displayScore(track: DomainTrack): String {
        val score = track.score

        return when (scoreFormat) {
            POINT_5 -> when (score) {
                0.0 -> "0 ★"
                else -> "${((score + 10) / 20).toInt()} ★"
            }

            POINT_3 -> when {
                score == 0.0 -> "0"
                score <= 35 -> "😦"
                score <= 60 -> "😐"
                else -> "😊"
            }

            else -> track.toApiScore()
        }
    }

    private suspend fun add(track: Track): Track {
        logcat { "add" }
        return api.addLibManga(track)
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        // If user was using API v1 fetch library_id
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api.findLibManga(track, userId ?: return track)
                ?: throw Exception("$track not found on user library")
            track.library_id = libManga.library_id
        }

        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun delete(track: DomainTrack) {
        if (track.libraryId == null || track.libraryId == 0L) {
            val libManga = api.findLibManga(track.toDbTrack(), userId ?: return) ?: return
            return api.deleteLibManga(track.copy(id = libManga.library_id!!))
        }

        api.deleteLibManga(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findLibManga(track, userId!!)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track, userId ?: return track)
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) = throw UnsupportedOperationException()

    override fun logout() {
        super.logout()
        interceptor.updateToken(null)
        authSessionPreference.set(null)
    }
}
