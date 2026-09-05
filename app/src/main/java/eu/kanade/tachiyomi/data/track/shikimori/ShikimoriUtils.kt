package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import mihon.graphql.shikimori.ShikimoriGetLibMangaQuery
import mihon.graphql.shikimori.ShikimoriGetMangaDetailsQuery
import mihon.graphql.shikimori.ShikimoriSearchMangaQuery
import mihon.graphql.shikimori.fragment.MangaFragment
import mihon.graphql.shikimori.type.MangaKindEnum
import mihon.graphql.shikimori.type.MangaStatusEnum
import mihon.graphql.shikimori.type.UserRateStatusEnum

fun ShikimoriGetLibMangaQuery.Manga.toTrack(trackId: Long): Track {
    val sManga = this
    return Track.create(trackId).apply {
        remote_id = sManga.id.toLong()
        title = sManga.name
        total_chapters = sManga.chapters.toLong()
        tracking_url = sManga.url

        if (sManga.userRate != null) {
            // null if not in user's list, must not throw here because it'd break adding titles
            // throws in the findLibManga method of ShikimoriApi if null and shouldn't be
            library_id = sManga.userRate.id.toLong()
            last_chapter_read = sManga.userRate.chapters.toDouble()
            score = sManga.userRate.score.toDouble()
            status = sManga.userRate.status.toLocalStatus()
        }
    }
}

fun ShikimoriGetMangaDetailsQuery.Manga.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

fun ShikimoriSearchMangaQuery.Manga.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

private fun MangaFragment.toTrackSearch(trackId: Long): TrackSearch {
    val sManga = this
    return TrackSearch.create(trackId).apply {
        remote_id = sManga.id.toLong()
        title = sManga.name
        total_chapters = sManga.chapters.toLong()
        cover_url = sManga.poster?.mainUrl.orEmpty()
        summary = sManga.description.orEmpty()
        score = sManga.score?.takeIf { it > 0.0 } ?: -1.0
        tracking_url = sManga.url
        publishing_status = sManga.status.toLocalString()
        publishing_type = sManga.kind.toLocalString()
        start_date = sManga.airedOn?.date.orEmpty()
        authors = sManga.personRoles?.filter { it.rolesEn.contains("Story") }?.map { it.person.name } ?: emptyList()
        artists = sManga.personRoles?.filter { it.rolesEn.contains("Art") }?.map { it.person.name } ?: emptyList()
    }
}

private fun MangaKindEnum?.toLocalString() = when (this) {
    MangaKindEnum.manga -> "Manga"
    MangaKindEnum.manhwa -> "Manhwa"
    MangaKindEnum.manhua -> "Manhua"
    MangaKindEnum.one_shot -> "Oneshot"
    MangaKindEnum.doujin -> "Doujin"
    // light novel & novel omitted by query filter, fall back to empty if included
    else -> ""
}

private fun MangaStatusEnum?.toLocalString() = when (this) {
    MangaStatusEnum.anons -> "Planned"
    MangaStatusEnum.ongoing -> "Ongoing"
    MangaStatusEnum.released -> "Released"
    MangaStatusEnum.paused -> "Paused"
    MangaStatusEnum.discontinued -> "Discontinued"
    else -> ""
}

private fun UserRateStatusEnum.toLocalStatus() = when (this) {
    UserRateStatusEnum.planned -> Shikimori.PLAN_TO_READ
    UserRateStatusEnum.watching -> Shikimori.READING
    UserRateStatusEnum.rewatching -> Shikimori.REREADING
    UserRateStatusEnum.completed -> Shikimori.COMPLETED
    UserRateStatusEnum.on_hold -> Shikimori.ON_HOLD
    UserRateStatusEnum.dropped -> Shikimori.DROPPED
    else -> throw NotImplementedError("Unknown status: $this")
}

fun Track.toShikimoriStatus() = when (status) {
    // rawValues because Shikimori doesn't have GraphQL mutations and this goes through the v2 API
    // if/when Shikimori adds GraphQL mutations, this should return UserRateStatusEnum members
    Shikimori.READING -> UserRateStatusEnum.watching.rawValue
    Shikimori.COMPLETED -> UserRateStatusEnum.completed.rawValue
    Shikimori.ON_HOLD -> UserRateStatusEnum.on_hold.rawValue
    Shikimori.DROPPED -> UserRateStatusEnum.dropped.rawValue
    Shikimori.PLAN_TO_READ -> UserRateStatusEnum.planned.rawValue
    Shikimori.REREADING -> UserRateStatusEnum.rewatching.rawValue
    else -> throw NotImplementedError("Unknown status: $status")
}
