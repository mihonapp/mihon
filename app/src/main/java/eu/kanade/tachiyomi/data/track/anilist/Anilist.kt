package eu.kanade.tachiyomi.data.track.anilist

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class Anilist(id: Long) : BaseTracker(id, "AniList"), DeletableTracker {

    companion object {
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
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(this, getPassword()) }

    private val api by lazy { AnilistApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val scorePreference = trackPreferences.anilistScoreType()

    init {
        // If the preference is an int from APIv1, logout user to force using APIv2
        try {
            scorePreference.get()
        } catch (e: ClassCastException) {
            logout()
            scorePreference.delete()
        }
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
        return when (scorePreference.get()) {
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
        return when (scorePreference.get()) {
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

        return when (scorePreference.get()) {
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
        return api.addLibManga(track)
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        // If user was using API v1 fetch library_id
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api.findLibManga(track, getUsername().toInt())
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
            val libManga = api.findLibManga(track.toDbTrack(), getUsername().toInt()) ?: return
            return api.deleteLibManga(track.copy(id = libManga.library_id!!))
        }

        api.deleteLibManga(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findLibManga(track, getUsername().toInt())
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
        val remoteTrack = api.getLibManga(track, getUsername().toInt())
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(token: String) {
        try {
            val oauth = api.createOAuth(token)
            interceptor.setAuth(oauth)
            val (username, scoreType) = api.getCurrentUser()
            scorePreference.set(scoreType)
            saveCredentials(username.toString(), oauth.accessToken)
        } catch (e: Throwable) {
            logout()
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun saveOAuth(alOAuth: ALOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(alOAuth))
    }

    fun loadOAuth(): ALOAuth? {
        return try {
            json.decodeFromString<ALOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }
}
