package eu.kanade.tachiyomi.data.track.comick

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import kotlin.time.Clock
import tachiyomi.domain.track.model.Track as DomainTrack

class Comick(id: Long) : BaseTracker(id, "Comick"), DeletableTracker {

    private val json: Json by injectLazy()

    private val interceptor by lazy { ComickInterceptor(this) }
    private val api by lazy { ComickApi(id, client, interceptor) }

    override fun getLogo() = R.drawable.brand_comick

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        PLAN_TO_READ -> MR.strings.plan_to_read
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1L

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> = IntRange(0, 10).map(Int::toString).toList()

    override fun displayScore(track: DomainTrack): String = track.score.toInt().toString()

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED && track.status != READING && didReadChapter) {
            track.status = READING
            if (track.last_chapter_read == 1.0) {
                track.started_reading_date = Clock.System.now().toEpochMilliseconds()
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api.findLibManga(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)

            update(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0

            api.addLibManga(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().let { hid ->
                return api.getMangaDetails(hid)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibManga(track) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteTrack)
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.getAccessToken(code)
            interceptor.setAuth(oauth)
            val currentUser = api.getCurrentUser()
            saveDisplayUsername(currentUser.username ?: currentUser.id)
            saveCredentials("user", oauth.accessToken)
        } catch (e: Exception) {
            logout()
            // gets shown by [TrackLoginActivity]
            if (e is ComickMissingScopesException) throw e
        }
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()
        saveDisplayUsername(currentUser.username ?: currentUser.id)
    }

    fun saveToken(oauth: ComickOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): ComickOAuth? {
        return try {
            json.decodeFromString(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }

    fun verifyOAuthState(state: String): Boolean = api.verifyOAuthState(state)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteLibManga(track)
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L

        private const val SEARCH_ID_PREFIX = "id:"
    }
}
