package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.AniListGetLibMangaQuery
import mihon.graphql.anilist.AniListGetMangaDetailsQuery
import mihon.graphql.anilist.AniListSearchMangaQuery
import mihon.graphql.anilist.fragment.MangaFragment
import mihon.graphql.anilist.type.MediaListStatus
import mihon.graphql.anilist.type.ScoreFormat
import tachiyomi.domain.track.model.Track as DomainTrack

fun AniListGetCurrentUserQuery.Viewer.toALUser(): ALUser {
    return ALUser(
        id = id,
        name = name,
        scoreFormat = mediaListOptions?.scoreFormat?.rawValue ?: ScoreFormat.POINT_10_DECIMAL.rawValue,
    )
}

fun AniListGetLibMangaQuery.MediaList.toTrack(trackId: Long): Track {
    val mediaList = this
    requireNotNull(mediaList.media) { "Missing Media data from AniList" }

    return mediaList.media.mangaFragment
        .toTrackSearch(trackId)
        .apply {
            library_id = mediaList.id.toLong()
            status = when (mediaList.status) {
                MediaListStatus.CURRENT -> Anilist.READING
                MediaListStatus.COMPLETED -> Anilist.COMPLETED
                MediaListStatus.PAUSED -> Anilist.ON_HOLD
                MediaListStatus.DROPPED -> Anilist.DROPPED
                MediaListStatus.PLANNING -> Anilist.PLAN_TO_READ
                MediaListStatus.REPEATING -> Anilist.REREADING
                else -> throw NotImplementedError("Unknown status: ${mediaList.status?.rawValue}")
            }
            score = mediaList.scoreRaw ?: 0.0
            last_chapter_read = mediaList.progress?.toDouble() ?: 0.0

            started_reading_date = mediaList.startedAt.let {
                if (it == null || it.year == null || it.month == null || it.day == null) {
                    0
                } else {
                    LocalDate(it.year, it.month, it.day)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
            }

            finished_reading_date = mediaList.completedAt.let {
                if (it == null || it.year == null || it.month == null || it.day == null) {
                    0
                } else {
                    LocalDate(it.year, it.month, it.day)
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
            }

            private = mediaList.private ?: false
        }
}

fun AniListSearchMangaQuery.Medium.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

fun AniListGetMangaDetailsQuery.Medium.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

private fun MangaFragment.toTrackSearch(trackId: Long): TrackSearch {
    val alManga = this
    return TrackSearch.create(trackId).apply {
        remote_id = alManga.id.toLong()
        title = alManga.title?.userPreferred ?: "(no title)"
        total_chapters = alManga.chapters?.toLong() ?: 0
        cover_url = alManga.coverImage?.large ?: ""
        summary = alManga.description?.htmlDecode() ?: ""
        score = alManga.averageScore?.toDouble() ?: -1.0
        tracking_url = "https://anilist.co/manga/$remote_id"
        publishing_status = alManga.status?.rawValue ?: ""
        publishing_type = if (alManga.format == null) {
            "Manga"
        } else if (alManga.format.rawValue != "MANGA") {
            alManga.format.rawValue.replace("_", "-")
        } else {
            when (alManga.countryOfOrigin) {
                "KR" -> "Manhwa"
                "CN", "TW" -> "Manhua"
                else -> "Manga"
            }
        }
        start_date = alManga.startDate?.let {
            buildString {
                it.year?.let { year -> append(year) }
                it.month?.let { month -> append("-$month") }
                it.day?.let { day -> append("-$day") }
            }.trim('-')
        } ?: ""

        authors = alManga.staff?.edges
            ?.filter { "Story" in (it?.role ?: "") }
            ?.mapNotNull { it?.node?.name?.userPreferred }
            ?: emptyList()

        artists = alManga.staff?.edges
            ?.filter { "Art" in (it?.role ?: "") }
            ?.mapNotNull { it?.node?.name?.userPreferred }
            ?: emptyList()
    }
}

fun Track.toApiStatus(): MediaListStatus = when (status) {
    Anilist.READING -> MediaListStatus.CURRENT
    Anilist.COMPLETED -> MediaListStatus.COMPLETED
    Anilist.ON_HOLD -> MediaListStatus.PAUSED
    Anilist.DROPPED -> MediaListStatus.DROPPED
    Anilist.PLAN_TO_READ -> MediaListStatus.PLANNING
    Anilist.REREADING -> MediaListStatus.REPEATING
    else -> throw NotImplementedError("Unknown status: $status")
}

fun DomainTrack.toApiScore(preferences: TrackPreferences): String = when (preferences.anilistScoreType.get()) {
    // 10 point
    "POINT_10" -> (score.toInt() / 10).toString()
    // 100 point
    "POINT_100" -> score.toInt().toString()
    // 5 stars
    "POINT_5" -> when {
        score == 0.0 -> "0"
        score < 30 -> "1"
        score < 50 -> "2"
        score < 70 -> "3"
        score < 90 -> "4"
        else -> "5"
    }
    // Smiley
    "POINT_3" -> when {
        score == 0.0 -> "0"
        score <= 35 -> ":("
        score <= 60 -> ":|"
        else -> ":)"
    }
    // 10 point decimal
    "POINT_10_DECIMAL" -> (score / 10).toString()
    else -> throw NotImplementedError("Unknown score type")
}
