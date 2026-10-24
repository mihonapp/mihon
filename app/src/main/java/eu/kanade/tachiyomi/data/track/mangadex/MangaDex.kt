package eu.kanade.tachiyomi.data.track.mangadex

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import kotlin.time.Clock
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaDex(id: Long) : BaseTracker(id, "MangaDex"), DeletableTracker {

    private val json: Json by injectLazy()

    private val interceptor by lazy { MangaDexInterceptor(this) }
    private val api by lazy { MangaDexApi(id, client, interceptor) }

    override fun getLogo() = R.drawable.brand_mangadex

    override fun getStatusList(): List<Long> {
        return listOf(READING, PLAN_TO_READ, COMPLETED, ON_HOLD, DROPPED, RE_READING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        RE_READING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = RE_READING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: DomainTrack): String = ""

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status !in arrayOf(COMPLETED, READING, RE_READING) && didReadChapter) {
            track.status = READING
            if (track.last_chapter_read == 1.0) {
                track.started_reading_date = Clock.System.now().toEpochMilliseconds()
            }
        }

        val uuid = track.tracking_url.getUuidFromTrackingUrlOrNull()
            ?: throw IllegalArgumentException("Not a MangaDex tracking URL")

        api.setStatus(uuid, track.status)

        if (track.last_chapter_read > 0) {
            val lastChapterRead = track.last_chapter_read.toFloat()
            val chapterIds = api.getChapters(uuid, upToChapter = lastChapterRead)
                .filter { it.number in 0f..lastChapterRead }
                .map { it.id }
            if (chapterIds.isNotEmpty()) {
                api.markChaptersRead(uuid, chapterIds)
            }
        }

        return track
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val uuid = track.tracking_url.getUuidFromTrackingUrlOrNull()
            ?: throw IllegalArgumentException("Not a MangaDex tracking URL")

        val remoteStatus = api.getStatusOrNull(uuid)
        return if (remoteStatus != null) {
            track.status = remoteStatus.fromMdStatus()
            track
        } else {
            api.followManga(uuid)
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            api.setStatus(uuid, track.status)
            track
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().let { id ->
                val uuid = id.getUuidFromTrackingUrlOrNull()
                    ?: id.takeIf { UUID_PATTERN.matches(it) }
                    ?: return emptyList()
                return api.getMangaDetails(uuid)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val uuid = track.tracking_url.getUuidFromTrackingUrlOrNull()
            ?: throw IllegalArgumentException("Not a MangaDex tracking URL")

        api.getStatusOrNull(uuid)?.let { track.status = it.fromMdStatus() }

        val readChapterIds = api.getReadChapterIds(uuid)
        if (readChapterIds.isNotEmpty()) {
            val chapters = api.getChapters(uuid)
            val readNumbers = chapters
                .filter { it.id in readChapterIds && it.number >= 0f }
                .map { it.number }
            if (readNumbers.isNotEmpty()) {
                track.last_chapter_read = readNumbers.max().toDouble()
            }
            chapters.maxOfOrNull { it.number }?.let {
                if (it >= 0f) track.total_chapters = it.toLong()
            }
        }

        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.getAccessToken(code)
            interceptor.setAuth(oauth)
            val currentUser = api.getCurrentUser()
            saveDisplayUsername(currentUser.data.username)
            saveCredentials("user", oauth.accessToken)
        } catch (e: Exception) {
            logout()
        }
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()
        saveDisplayUsername(currentUser.data.username)
    }

    fun saveToken(oauth: MangaDexOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): MangaDexOAuth? {
        return try {
            json.decodeFromString(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    override suspend fun delete(track: DomainTrack) {
        val uuid = track.remoteUrl.getUuidFromTrackingUrlOrNull()
            ?: throw IllegalArgumentException("Not a MangaDex tracking URL")
        api.unfollowManga(uuid)
    }

    companion object {
        const val READING = 1L
        const val PLAN_TO_READ = 2L
        const val COMPLETED = 3L
        const val ON_HOLD = 4L
        const val DROPPED = 5L
        const val RE_READING = 6L

        private const val SEARCH_ID_PREFIX = "id:"

        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        )
    }
}
