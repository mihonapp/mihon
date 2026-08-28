package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUser
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import mihon.graphql.kitsu.KitsuFindLibMangaQuery
import mihon.graphql.kitsu.KitsuGetCurrentAccountQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuSearchMangaByTitleQuery
import mihon.graphql.kitsu.fragment.MangaFragment
import mihon.graphql.kitsu.type.LibraryEntryStatusEnum
import mihon.graphql.kitsu.type.MangaSubtypeEnum
import mihon.graphql.kitsu.type.ReleaseStatusEnum
import kotlin.time.Instant

fun Track.toKitsuStatus() = when (this.status) {
    Kitsu.READING -> LibraryEntryStatusEnum.CURRENT
    Kitsu.PLAN_TO_READ -> LibraryEntryStatusEnum.PLANNED
    Kitsu.COMPLETED -> LibraryEntryStatusEnum.COMPLETED
    Kitsu.ON_HOLD -> LibraryEntryStatusEnum.ON_HOLD
    Kitsu.DROPPED -> LibraryEntryStatusEnum.DROPPED
    else -> throw Exception("Unknown status: ${this.status}")
}

fun LibraryEntryStatusEnum.toLocalStatus(): Long = when (this) {
    LibraryEntryStatusEnum.CURRENT -> Kitsu.READING
    LibraryEntryStatusEnum.PLANNED -> Kitsu.PLAN_TO_READ
    LibraryEntryStatusEnum.COMPLETED -> Kitsu.COMPLETED
    LibraryEntryStatusEnum.ON_HOLD -> Kitsu.ON_HOLD
    LibraryEntryStatusEnum.DROPPED -> Kitsu.DROPPED
    else -> throw Exception("Unknown status: $this")
}

fun KitsuGetCurrentAccountQuery.CurrentAccount.toKitsuUser(): KitsuUser {
    return KitsuUser(
        id = id,
        name = profile.name,
        ratingSystem = ratingSystem.rawValue,
    )
}

fun KitsuGetMangaDetailsByIdQuery.FindMangaById.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

fun KitsuGetMangaDetailsBySlugQuery.FindMangaBySlug.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

fun KitsuSearchMangaByTitleQuery.Node.toTrackSearch(trackId: Long): TrackSearch {
    return mangaFragment.toTrackSearch(trackId)
}

fun KitsuFindLibMangaQuery.FindMangaById.toTrackSearch(trackId: Long): TrackSearch? {
    if (myLibraryEntry == null) return null

    return mangaFragment.toTrackSearch(trackId).apply {
        library_id = myLibraryEntry.id.toLong()
        started_reading_date = myLibraryEntry.startedAt?.toEpochMilliseconds() ?: 0
        finished_reading_date = myLibraryEntry.finishedAt?.toEpochMilliseconds() ?: 0
        status = myLibraryEntry.status.toLocalStatus()
        score = myLibraryEntry.rating?.toDouble() ?: 0.0
        last_chapter_read = myLibraryEntry.progress.toDouble()
        private = myLibraryEntry.private
    }
}

private fun MangaFragment.toTrackSearch(trackId: Long): TrackSearch {
    val kitsuManga = this
    return TrackSearch.create(trackId).apply {
        remote_id = kitsuManga.id.toLong()
        title = kitsuManga.titles.preferred
        total_chapters = kitsuManga.chapterCount?.toLong() ?: 0
        cover_url = kitsuManga.posterImage?.views?.firstOrNull()?.url ?: kitsuManga.posterImage?.original?.url.orEmpty()
        summary = (kitsuManga.description["en"] as? String).orEmpty()
        tracking_url = "https://kitsu.app/manga/${kitsuManga.slug}"
        score = kitsuManga.averageRating ?: -1.0
        publishing_status = when (kitsuManga.status) {
            ReleaseStatusEnum.TBA -> ReleaseStatusEnum.TBA.rawValue
            else -> kitsuManga.status.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        }
        publishing_type = when (kitsuManga.subtype) {
            MangaSubtypeEnum.OEL -> MangaSubtypeEnum.OEL.rawValue
            else -> kitsuManga.subtype.rawValue.lowercase().replaceFirstChar { it.uppercase() }
        }
        start_date = kitsuManga.startDate.orEmpty()
        authors = kitsuManga.staff.nodes
            ?.filterNotNull()
            ?.filter { it.role.contains("Story") }
            ?.map { it.person.name }
            ?: emptyList()
        artists = kitsuManga.staff.nodes
            ?.filterNotNull()
            ?.filter { it.role.contains("Art") }
            ?.map { it.person.name }
            ?: emptyList()
    }
}
