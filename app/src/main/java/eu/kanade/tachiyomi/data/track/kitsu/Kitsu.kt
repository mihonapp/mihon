package eu.kanade.tachiyomi.data.track.kitsu

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import tachiyomi.domain.track.model.Track as DomainTrack

class Kitsu(id: Long) : BaseTracker(id, "Kitsu"), DeletableTracker {

    private data class RatingSystem(
        val name: String,
        val scoreList: List<String>,
        val twentyScale: List<Int>,
    )

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L

        const val RATING_SIMPLE = "simple"
        const val RATING_REGULAR = "regular"
        const val RATING_ADVANCED = "advanced"

        private val ratingSystems = mapOf(
            // Smileys
            RATING_SIMPLE to RatingSystem(
                name = RATING_SIMPLE,
                scoreList = listOf("-", "😡", "😐", "😊", "😀"),
                twentyScale = (2..20 step 6).toList(), // 2, 8, 14, 20
            ),
            // DecimalFormatter is not thread safe, so new formatters for each map instead of extracted val attribute
            // to not incite reuse
            // Stars (0.5-5 step 0.5)
            RATING_REGULAR to RatingSystem(
                name = RATING_REGULAR,
                scoreList = (0..10).map { it / 2.0 }.map(DecimalFormat("0.#")::format).map { "$it ★" },
                twentyScale = (2..20 step 2).toList(), // 2, 4, ..., 18, 20
            ),
            // 10 point decimal (step 0.5, starting at 1) + 0 for our "not rated" placeholder
            RATING_ADVANCED to RatingSystem(
                name = RATING_ADVANCED,
                scoreList = listOf("0") + (2..20).map { it / 2.0 }.map(DecimalFormat("0.#")::format),
                twentyScale = (2..20).toList(), // 2, 3, ..., 19, 20
            ),
        )

        private const val SEARCH_ID_PREFIX = "id:"
    }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val json: Json by injectLazy()

    private val interceptor by lazy { KitsuInterceptor(this) }

    private val api by lazy { KitsuApi(id, client, interceptor) }

    private val scorePreference by lazy { trackPreferences.kitsuScoreType }

    override fun getLogo() = R.drawable.brand_kitsu

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    private fun getCurrentRatingSystem(): RatingSystem {
        val ratingSystem = scorePreference.get()
        return ratingSystems[ratingSystem] ?: throw Exception("Unknown score type $ratingSystem")
    }

    override fun getScoreList(): List<String> = getCurrentRatingSystem().scoreList

    override fun get10PointScore(track: DomainTrack): Double {
        // score is stored in Kitsu's native 2-20 scale
        return track.score / 2.0
    }

    override fun indexToScore(index: Int): Double {
        if (index == 0) return 0.0
        return getCurrentRatingSystem().twentyScale[index - 1].toDouble()
    }

    override fun displayScore(track: DomainTrack): String {
        val ratingSystem = getCurrentRatingSystem()
        // Since Kitsu's valid score range is 2-20, unset values of -1.0 or 0.0 will both return -1 from indexOfLast
        // which is turned into index 0 of the scoreList, giving us the "unset" display score (- or 0).
        // Proper scores are "rounded down" to the nearest value of the scale (also what Kitsu's website does)
        return ratingSystem.scoreList[ratingSystem.twentyScale.indexOfLast { it <= track.score } + 1]
    }

    private suspend fun add(track: Track): Track {
        return api.addLibManga(track)
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else {
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
        api.removeLibManga(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findLibManga(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.remote_id = remoteTrack.remote_id
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().let { id ->
                return api.getMangaDetails(id)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibManga(track) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        val token = api.login(username, password)
        interceptor.newAuth(token)
        val currentUser = api.getCurrentUser()

        val ratingSystem = currentUser.ratingSystem
        if (ratingSystem.lowercase() in listOf(RATING_SIMPLE, RATING_REGULAR, RATING_ADVANCED)) {
            scorePreference.set(ratingSystem)
        } else {
            logcat(LogPriority.ERROR) { "Unsupported Kitsu score type: $ratingSystem" }
            scorePreference.set(RATING_ADVANCED)
        }
        saveDisplayUsername(currentUser.profile.name)
        saveCredentials(username, currentUser.id)
    }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    fun saveToken(oauth: KitsuOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): KitsuOAuth? {
        return try {
            json.decodeFromString<KitsuOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }
}
