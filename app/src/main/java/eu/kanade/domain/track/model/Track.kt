package eu.kanade.domain.track.model

import tachiyomi.domain.track.model.Track
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

fun Track.copyPersonalFrom(other: Track): Track {
    return this.copy(
        lastChapterRead = other.lastChapterRead,
        score = other.score,
        status = other.status,
        startDate = other.startDate,
        finishDate = other.finishDate,
    )
}

fun Track.toDbTrack(): DbTrack = DbTrack.create(syncId).also {
    it.id = id
    it.manga_id = mangaId
    it.media_id = remoteId
    it.library_id = libraryId
    it.title = title
    it.last_chapter_read = lastChapterRead.toFloat()
    it.total_chapters = totalChapters.toInt()
    it.status = status.toInt()
    it.score = score.toFloat()
    it.tracking_url = remoteUrl
    it.started_reading_date = startDate
    it.finished_reading_date = finishDate
}

fun DbTrack.toDomainTrack(idRequired: Boolean = true): Track? {
    val trackId = id ?: if (idRequired.not()) -1 else return null
    return Track(
        id = trackId,
        mangaId = manga_id,
        syncId = sync_id.toLong(),
        remoteId = media_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read.toDouble(),
        totalChapters = total_chapters.toLong(),
        status = status.toLong(),
        score = score.toDouble(),
        remoteUrl = tracking_url,
        startDate = started_reading_date,
        finishDate = finished_reading_date,
    )
}
