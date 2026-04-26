package eu.kanade.tachiyomi.data.track.hikka

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class Hikka(id: Long) : BaseTracker(id, "Hikka"), DeletableTracker {

    companion object {
        const val READING = 0L
        const val COMPLETED = 1L
        const val ON_HOLD = 2L
        const val DROPPED = 3L
        const val PLAN_TO_READ = 4L
        const val REREADING = 5L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { HikkaInterceptor(this) }

    private val api by lazy { HikkaApi(id, client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override fun getLogo(): Int = R.drawable.brand_hikka

    override fun getStatusList(): List<Long> {
        return listOf(
            READING,
            COMPLETED,
            ON_HOLD,
            DROPPED,
            PLAN_TO_READ,
            REREADING,
        )
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

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack): String {
        return track.score.toInt().toString()
    }

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
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
        return api.updateUserManga(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val readContent = api.getRead(track)
        val remoteTrack = api.getManga(track)

        track.copyPersonalFrom(remoteTrack)
        track.remote_id = remoteTrack.remote_id
        track.library_id = remoteTrack.library_id

        if (track.status != COMPLETED) {
            val isRereading = track.status == REREADING
            track.status = if (!isRereading && hasReadChapters) READING else track.status
        }

        return if (readContent != null) {
            track.score = readContent.score.toDouble()
            track.last_chapter_read = readContent.chapters.toDouble()
            track.started_reading_date = (readContent.startDate ?: 0L) * 1000
            track.finished_reading_date = (readContent.endDate ?: 0L) * 1000
            update(track)
        } else {
            track.score = 0.0
            update(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> = api.searchManga(query)

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters

        val readContent = api.getRead(track)
        if (readContent != null) {
            track.score = readContent.score.toDouble()
            track.last_chapter_read = readContent.chapters.toDouble()
            track.status = toTrackStatus(readContent.status)
            track.started_reading_date = (readContent.startDate ?: 0L) * 1000
            track.finished_reading_date = (readContent.endDate ?: 0L) * 1000
        }

        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(reference: String) {
        try {
            val oauth = api.accessToken(reference)
            interceptor.setAuth(oauth)
            val user = api.getCurrentUser()
            saveCredentials(user.reference, oauth.accessToken)
        } catch (e: Throwable) {
            logout()
        }
    }

    override suspend fun delete(track: DomainTrack) = api.deleteUserManga(track)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: HKOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): HKOAuth? {
        return try {
            json.decodeFromString<HKOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }
}
