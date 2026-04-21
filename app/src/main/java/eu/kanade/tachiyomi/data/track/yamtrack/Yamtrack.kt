package eu.kanade.tachiyomi.data.track.yamtrack

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.yamtrack.dto.copyToTrack
import eu.kanade.tachiyomi.data.track.yamtrack.dto.resolveTotalChapters
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import tachiyomi.domain.track.model.Track as DomainTrack

class Yamtrack(id: Long) : BaseTracker(id, "Yamtrack"), DeletableTracker {

    override val supportsReadingDates: Boolean = true

    companion object {
        const val PLANNING = 1L
        const val READING = 2L
        const val COMPLETED = 3L
        const val PAUSED = 4L
        const val DROPPED = 5L

        const val SOURCE_MANUAL = "manual"

        // Yamtrack's API represents statuses as integers (0-4); see MEDIA_STATUS_MAP in api/helpers.py.
        private const val API_STATUS_PLANNING = 0
        private const val API_STATUS_IN_PROGRESS = 1
        private const val API_STATUS_PAUSED = 2
        private const val API_STATUS_COMPLETED = 3
        private const val API_STATUS_DROPPED = 4

        private val SCORE_LIST = (0..10)
            .flatMap { decimal ->
                when (decimal) {
                    10 -> listOf("10.0")
                    else -> (0..9).map { fraction -> "$decimal.$fraction" }
                }
            }
            .toImmutableList()

        fun statusToApi(status: Long): Int = when (status) {
            PLANNING -> API_STATUS_PLANNING
            READING -> API_STATUS_IN_PROGRESS
            COMPLETED -> API_STATUS_COMPLETED
            PAUSED -> API_STATUS_PAUSED
            DROPPED -> API_STATUS_DROPPED
            else -> API_STATUS_PLANNING
        }

        fun statusFromApi(status: Int?): Long = when (status) {
            API_STATUS_PLANNING -> PLANNING
            API_STATUS_IN_PROGRESS -> READING
            API_STATUS_COMPLETED -> COMPLETED
            API_STATUS_PAUSED -> PAUSED
            API_STATUS_DROPPED -> DROPPED
            else -> PLANNING
        }

        fun buildRemoteId(source: String, mediaId: String): Long {
            // Track.remote_id is a Long, but Yamtrack identifies entries by (source, media_id).
            // We derive a stable non-negative Long from the pair so it can be used as a key.
            val hash = "$source:$mediaId".hashCode().toLong()
            return hash and 0x7fffffffffffffffL
        }

        fun buildTrackingUrl(baseUrl: String, source: String, mediaId: String, title: String? = null): String {
            val base = baseUrl.trimEnd('/')
            val sourceSeg = encodeSegment(source)
            val idSeg = encodeSegment(mediaId)
            val slug = title?.let { slugify(it) }.orEmpty()
            return if (slug.isNotEmpty()) {
                "$base/details/$sourceSeg/manga/$idSeg/$slug"
            } else {
                "$base/details/$sourceSeg/manga/$idSeg"
            }
        }

        /**
         * Parses a Yamtrack tracking URL and returns the decoded `(source, mediaId)`.
         * Accepts both the current `/details/{source}/manga/{mediaId}[/{slug}]` format and
         * the legacy `/media/manga/{source}/{mediaId}` format (for tracks saved before the fix).
         */
        fun parseTrackingUrl(url: String): Pair<String, String>? {
            if (url.isBlank()) return null
            Regex("""/details/([^/]+)/manga/([^/?#]+)""").find(url)?.let {
                return decodeSegment(it.groupValues[1]) to decodeSegment(it.groupValues[2])
            }
            Regex("""/media/manga/([^/]+)/([^/?#]+)""").find(url)?.let {
                return decodeSegment(it.groupValues[1]) to decodeSegment(it.groupValues[2])
            }
            return null
        }

        private fun encodeSegment(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

        private fun decodeSegment(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8)

        private fun slugify(title: String): String {
            // Approximate Django's slugify (Yamtrack is a Django app): strip diacritics,
            // lowercase, keep [a-z0-9], collapse whitespace/hyphens to single '-'.
            val normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return normalized
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("[\\s-]+"), "-")
                .trim('-')
        }

        fun formatIsoDate(epochMillis: Long): String? {
            if (epochMillis <= 0L) return null
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(epochMillis)
            }.getOrNull()
        }

        fun parseIsoDate(value: String): Long {
            // Yamtrack returns ISO dates (YYYY-MM-DD) or ISO datetimes. Take the date portion.
            val dateOnly = value.take(10)
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateOnly)?.time ?: 0L
            }.getOrDefault(0L)
        }
    }

    private val interceptor by lazy { YamtrackInterceptor(this) }

    private val api by lazy { YamtrackApi(this, client, interceptor) }

    override fun getLogo(): Int = R.drawable.brand_yamtrack

    override fun getStatusList(): List<Long> = listOf(PLANNING, READING, PAUSED, COMPLETED, DROPPED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        PLANNING -> MR.strings.plan_to_read
        READING -> MR.strings.reading
        PAUSED -> MR.strings.paused
        COMPLETED -> MR.strings.completed
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun indexToScore(index: Int): Double = SCORE_LIST[index].toDouble()

    override fun displayScore(track: DomainTrack): String {
        return if (track.score > 0.0) "%.1f".format(track.score) else "-"
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED && didReadChapter) {
            track.status = if (track.total_chapters > 0L &&
                track.last_chapter_read.toLong() == track.total_chapters
            ) {
                COMPLETED
            } else {
                READING
            }
        }
        val (source, mediaId) = parseTrackingUrl(track.tracking_url) ?: return track
        api.updateMedia(track, source, mediaId)
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val (source, mediaId) = parseTrackingUrl(track.tracking_url)
            ?: throw IllegalStateException("Invalid Yamtrack tracking URL: ${track.tracking_url}")

        val existing = api.getMediaItem(source, mediaId)
        // Yamtrack's GET endpoint returns 200 with provider metadata even when the user hasn't
        // tracked the item yet; `tracked` tells us whether it's actually in their library.
        return if (existing?.tracked == true) {
            existing.copyToTrack(track)
            track
        } else {
            track.total_chapters = resolveTotalChapters(existing?.maxProgress)
            if (track.status == 0L) {
                track.status = if (hasReadChapters) READING else PLANNING
            }
            api.addMedia(track, source, mediaId, track.title)
            track
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.isBlank()) return emptyList()
        val baseUrl = getBaseUrl().trimEnd('/')
        val remoteResults = try {
            api.search(query)
        } catch (e: Exception) {
            emptyList()
        }

        // Always offer a "manual entry" option so the user can track items that don't appear
        // in Yamtrack's upstream providers (matches Yamtrack's own `source=manual` flow).
        val manualEntry = TrackSearch.create(id).apply {
            remote_id = buildRemoteId(SOURCE_MANUAL, query)
            title = query
            cover_url = ""
            summary = ""
            tracking_url = buildTrackingUrl(baseUrl, SOURCE_MANUAL, query, query)
            publishing_type = "Manual entry"
        }
        return remoteResults + manualEntry
    }

    override suspend fun refresh(track: Track): Track {
        val (source, mediaId) = parseTrackingUrl(track.tracking_url) ?: return track
        val remote = api.getMediaItem(source, mediaId) ?: return track
        if (remote.tracked) {
            remote.copyToTrack(track)
        }
        return track
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteMedia(track)
    }

    override suspend fun login(username: String, password: String) {
        val baseUrl = normalizeBaseUrl(username)
        val token = password.trim()
        if (baseUrl.isEmpty() || token.isEmpty()) {
            throw IllegalArgumentException("Host URL and API token are required")
        }
        api.verifyCredentials(baseUrl, token)
        saveCredentials(baseUrl, token)
    }

    fun getBaseUrl(): String = getUsername()

    fun getApiToken(): String = getPassword()

    private fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
